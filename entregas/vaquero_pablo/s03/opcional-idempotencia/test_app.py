"""Pruebas reales de HTTP, concurrencia, persistencia y caída del proceso."""

from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
import http.client
import json
from pathlib import Path
import select
import sqlite3
import subprocess
import sys
import tempfile
import threading
import unittest

import app


ROOT = Path(__file__).resolve().parent
PAYLOAD = {"amount": 10000, "currency": "MXN"}
CRASH_CODE = """
import os, sys, app
db, key, phase = sys.argv[1:]
original_connect = app.connect
def crash_connection(*args, **kwargs):
    conn = original_connect(*args, **kwargs)
    prefix = {'after_key': 'INSERT INTO charges', 'after_charge': 'UPDATE idempotency_keys'}.get(phase)
    if prefix:
        def trace(sql):
            if sql.startswith(prefix):
                os._exit(73)
        conn.set_trace_callback(trace)
    return conn
app.connect = crash_connection
app.charge(db, key, {'amount': 10000, 'currency': 'MXN'})
os._exit(73)  # COMMIT realizado, pero no se entregó ninguna respuesta al cliente.
"""


@contextmanager
def running_server(db, *extra_args):
    with tempfile.TemporaryFile(mode="w+") as log:
        process = subprocess.Popen(
            [sys.executable, str(ROOT / "app.py"), "--db", str(db), "--port", "0", *extra_args],
            stdout=subprocess.PIPE, stderr=log, text=True,
        )
        try:
            ready, _, _ = select.select([process.stdout], [], [], 10)
            line = process.stdout.readline() if ready else ""
            if not line.startswith("Servidor: "):
                log.seek(0)
                raise RuntimeError("El servidor no inició: " + log.read())
            yield int(line.strip().rsplit(":", 1)[1])
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            process.stdout.close()


def request(port, key="order-1", payload=PAYLOAD, raw=None, path="/charges"):
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
    headers = {"Content-Type": "application/json"}
    if key is not None:
        headers["Idempotency-Key"] = key
    body = app.encode(payload) if raw is None else raw
    try:
        conn.request("POST", path, body=body, headers=headers)
        response = conn.getresponse()
        return response.status, dict(response.getheaders()), response.read()
    finally:
        conn.close()


class IdempotencyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.db = Path(self.temp.name) / "test.sqlite3"
        app.init_db(self.db)

    def rows(self, table):
        with sqlite3.connect(self.db) as conn:
            # table es una constante de las pruebas, no una entrada del cliente.
            return conn.execute(f"SELECT * FROM {table}").fetchall()

    def assert_one_charge(self):
        self.assertEqual(len(self.rows("charges")), 1)
        self.assertEqual(len(self.rows("idempotency_keys")), 1)

    def test_concurrent_http_requests_in_one_server(self):
        with running_server(self.db, "--delay", "0.2") as port:
            responses = self.concurrent_requests([port, port])
        self.assert_replay_pair(responses)

    def test_demo_cli(self):
        with running_server(self.db, "--delay", "0.2") as port:
            result = subprocess.run(
                [sys.executable, str(ROOT / "demo_race.py"), "--port", str(port)],
                capture_output=True, text=True, timeout=15,
            )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("OK: una creación y una repetición", result.stdout)
        self.assert_one_charge()
        print("\n" + result.stdout, end="")

    def test_concurrent_http_requests_in_two_processes(self):
        with running_server(self.db, "--delay", "0.2") as first:
            with running_server(self.db, "--delay", "0.2") as second:
                responses = self.concurrent_requests([first, second])
        self.assert_replay_pair(responses)

    def concurrent_requests(self, ports):
        barrier = threading.Barrier(len(ports))
        def send(port):
            barrier.wait(timeout=5)
            return request(port)
        with ThreadPoolExecutor(max_workers=len(ports)) as pool:
            return list(pool.map(send, ports))

    def assert_replay_pair(self, responses):
        self.assertEqual([r[0] for r in responses], [201, 201])
        self.assertEqual({r[1]["Idempotency-Replayed"] for r in responses}, {"true", "false"})
        self.assertEqual(responses[0][2], responses[1][2])
        self.assert_one_charge()

    def test_changed_payload_conflicts(self):
        with running_server(self.db) as port:
            self.assertEqual(request(port)[0], 201)
            status, _, _ = request(port, payload={"amount": 20000, "currency": "MXN"})
            self.assertEqual(status, 409)
        self.assert_one_charge()

    def test_concurrent_changed_payload_conflicts(self):
        with running_server(self.db, "--delay", "0.2") as port:
            barrier = threading.Barrier(2)
            def send(amount):
                barrier.wait(timeout=5)
                return request(port, payload={"amount": amount, "currency": "MXN"})
            with ThreadPoolExecutor(max_workers=2) as pool:
                responses = list(pool.map(send, [10000, 20000]))
        self.assertEqual(sorted(r[0] for r in responses), [201, 409])
        self.assert_one_charge()

    def test_json_spacing_and_field_order_do_not_change_identity(self):
        with running_server(self.db) as port:
            first = request(port)
            second = request(port, raw=b'{ "currency": "MXN", "amount": 10000 }')
        self.assert_replay_pair([first, second])

    def test_different_keys_are_different_operations(self):
        with running_server(self.db) as port:
            first = request(port, key="order-a")
            second = request(port, key="order-b")
        self.assertEqual([first[0], second[0]], [201, 201])
        self.assertNotEqual(json.loads(first[2])["id"], json.loads(second[2])["id"])
        self.assertEqual(len(self.rows("charges")), 2)

    def test_restart_keeps_original_response(self):
        with running_server(self.db) as port:
            first = request(port)
        with running_server(self.db) as port:
            second = request(port)
        self.assert_replay_pair([first, second])

    def test_invalid_input_does_not_reserve_key(self):
        with running_server(self.db) as port:
            for payload in [None, [], {}, {**PAYLOAD, "extra": 1}, {**PAYLOAD, "amount": True},
                            {**PAYLOAD, "amount": 1.5}, {**PAYLOAD, "amount": -1},
                            {**PAYLOAD, "currency": "xxx"}]:
                with self.subTest(payload=payload):
                    self.assertEqual(request(port, payload=payload)[0], 422)
            self.assertEqual(request(port, key=None)[0], 400)
            self.assertEqual(request(port, key="bad key")[0], 400)
            self.assertEqual(request(port, raw=b'{')[0], 400)
            self.assertEqual(request(port, path="/unknown")[0], 404)
            self.assertEqual(self.rows("idempotency_keys"), [])
            self.assertEqual(request(port)[0], 201)
        self.assert_one_charge()

    def test_lock_timeout_returns_retryable_503(self):
        with running_server(self.db, "--lock-timeout", "0.05") as port:
            lock = app.connect(self.db)
            try:
                lock.execute("BEGIN IMMEDIATE")
                status, headers, _ = request(port)
                self.assertEqual(status, 503)
                self.assertEqual(headers["Retry-After"], "1")
            finally:
                lock.rollback()
                lock.close()
            self.assertEqual(self.rows("idempotency_keys"), [])
            self.assertEqual(request(port)[0], 201)
        self.assert_one_charge()

    def crash(self, key, phase):
        result = subprocess.run(
            [sys.executable, "-c", CRASH_CODE, str(self.db), key, phase],
            cwd=ROOT, capture_output=True, text=True, timeout=10,
        )
        self.assertEqual(result.returncode, 73, result.stderr)

    def test_crash_before_commit_rolls_back_key_and_charge(self):
        for phase in ["after_key", "after_charge"]:
            with self.subTest(phase=phase):
                self.crash(phase, phase)
                with sqlite3.connect(self.db) as conn:
                    self.assertEqual(conn.execute(
                        "SELECT COUNT(*) FROM idempotency_keys WHERE key = ?", (phase,),
                    ).fetchone()[0], 0)
                    self.assertEqual(conn.execute(
                        "SELECT COUNT(*) FROM charges WHERE idempotency_key = ?", (phase,),
                    ).fetchone()[0], 0)
                status, _, replay = app.charge(self.db, phase, PAYLOAD)
                self.assertEqual(status, 201)
                self.assertFalse(replay)

    def test_crash_after_commit_replays_saved_response(self):
        self.crash("lost-response", "after_commit")
        original_body = self.rows("idempotency_keys")[0][3]
        with running_server(self.db) as port:
            status, headers, body = request(port, key="lost-response")
        self.assertEqual(status, 201)
        self.assertEqual(headers["Idempotency-Replayed"], "true")
        self.assertEqual(body, original_body)
        self.assert_one_charge()


if __name__ == "__main__":
    unittest.main(verbosity=2)
