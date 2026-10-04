"""Pruebas reales con HTTP, SQLite temporal y terminación abrupta de procesos."""

import concurrent.futures
import json
import multiprocessing
import os
import sqlite3
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from http.server import ThreadingHTTPServer

from app import Store, encode, handler_for

PAYLOAD = {'monto_centavos': 12500, 'moneda': 'MXN', 'referencia': 'pedido-001'}


def call(url, key='clave-prueba-001', payload=None, raw=None, content_type='application/json'):
    data = raw if raw is not None else encode(PAYLOAD if payload is None else payload).encode()
    headers = {'Content-Type': content_type}
    if key is not None:
        headers['Idempotency-Key'] = key
    request = urllib.request.Request(url + '/cobros', data=data, headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.read(), response.headers.get('Idempotency-Replayed')


def process_charge(path, gate, results):
    store = Store(path, delay=0.1)
    gate.wait(timeout=15)
    results.put(store.charge('clave-procesos-001', PAYLOAD))


class Idempotencia(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'cobros.sqlite3'
        self.store = Store(self.path, delay=0.1)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), handler_for(self.store))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temp.cleanup()

    def counts(self):
        with sqlite3.connect(self.path) as db:
            return tuple(db.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0]
                         for table in ('operaciones', 'cobros'))

    def simultaneous(self, count):
        gate = threading.Barrier(count)
        def request(_):
            gate.wait(timeout=10)
            return call(self.url)
        with concurrent.futures.ThreadPoolExecutor(max_workers=count) as pool:
            return list(pool.map(request, range(count)))

    def test_01_dos_solicitudes_http_simultaneas(self):
        results = self.simultaneous(2)
        self.assertEqual([result[0] for result in results], [201, 201])
        self.assertEqual(results[0][1], results[1][1])
        self.assertEqual(sorted(result[2] for result in results), ['false', 'true'])
        self.assertEqual(self.counts(), (1, 1))

    def test_02_doce_solicitudes_http_simultaneas(self):
        results = self.simultaneous(12)
        self.assertTrue(all(result[0] == 201 for result in results))
        self.assertEqual(len({result[1] for result in results}), 1)
        self.assertEqual(sum(result[2] == 'false' for result in results), 1)
        self.assertEqual(self.counts(), (1, 1))

    def test_03_replay_exacto_despues_de_reabrir_base(self):
        original = call(self.url)
        restarted = Store(self.path, delay=0)
        result = restarted.charge('clave-prueba-001', dict(reversed(list(PAYLOAD.items()))))
        self.assertEqual(result, (201, original[1].decode(), True))
        self.assertEqual(self.counts(), (1, 1))

    def test_04_payload_distinto_no_cobra(self):
        call(self.url)
        different = dict(PAYLOAD, monto_centavos=25000)
        self.assertEqual(call(self.url, payload=different)[0], 409)
        self.assertEqual(call(self.url)[0], 201)
        self.assertEqual(self.counts(), (1, 1))

    def test_05_validacion_sin_reservar_claves(self):
        invalid = [dict(PAYLOAD, monto_centavos=0), dict(PAYLOAD, monto_centavos=True),
                   dict(PAYLOAD, monto_centavos=1.5), dict(PAYLOAD, moneda='USD'),
                   dict(PAYLOAD, referencia=' '), dict(PAYLOAD, extra=1), {}]
        for payload in invalid:
            with self.subTest(payload=payload):
                self.assertEqual(call(self.url, payload=payload)[0], 400)
        for key in (None, 'corta', 'con espacios'):
            self.assertEqual(call(self.url, key=key)[0], 400)
        for raw in (b'{', b'\xff', b'{"monto_centavos":1,"monto_centavos":2}',
                    b'{"referencia":"\\ud800","moneda":"MXN","monto_centavos":1}'):
            self.assertEqual(call(self.url, raw=raw)[0], 400)
        self.assertEqual(call(self.url, content_type='text/plain')[0], 415)
        self.assertEqual(call(self.url, raw=b'x' * 4097)[0], 413)
        self.assertEqual(self.counts(), (0, 0))

    def crash(self, stage):
        worker = subprocess.run(
            [sys.executable, str(Path(__file__).resolve()), '--crash', str(self.path), stage],
            capture_output=True, timeout=15,
        )
        self.assertEqual(worker.returncode, 77, worker.stderr.decode())

    def test_06_caida_despues_insert_hace_rollback(self):
        self.crash('after_insert')
        self.assertEqual(self.counts(), (0, 0))
        self.assertEqual(call(self.url)[2], 'false')
        self.assertEqual(self.counts(), (1, 1))

    def test_07_caida_despues_cobro_antes_commit_hace_rollback(self):
        self.crash('after_charge')
        self.assertEqual(self.counts(), (0, 0))
        self.assertEqual(call(self.url)[2], 'false')
        self.assertEqual(self.counts(), (1, 1))

    def test_08_caida_despues_commit_antes_respuesta_reproduce(self):
        self.crash('after_commit')
        self.assertEqual(self.counts(), (1, 1))
        with sqlite3.connect(self.path) as db:
            saved = db.execute('SELECT respuesta FROM operaciones').fetchone()[0]
        result = call(self.url)
        self.assertEqual(result, (201, saved.encode(), 'true'))
        self.assertEqual(self.counts(), (1, 1))

    def test_09_unique_impide_reserva_duplicada_directa(self):
        call(self.url)
        with sqlite3.connect(self.path) as db:
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute("INSERT INTO operaciones (clave, solicitud, creada_en, estado) "
                           "VALUES ('clave-prueba-001', '{}', 'hoy', 'reservada')")
        self.assertEqual(self.counts(), (1, 1))

    def test_10_dos_procesos_comparten_base(self):
        context = multiprocessing.get_context('spawn')
        gate = context.Barrier(2)
        results = context.Queue()
        workers = [context.Process(target=process_charge, args=(str(self.path), gate, results))
                   for _ in range(2)]
        try:
            for worker in workers:
                worker.start()
            responses = [results.get(timeout=20) for _ in workers]
            for worker in workers:
                worker.join(timeout=10)
                self.assertEqual(worker.exitcode, 0)
            self.assertEqual(responses[0][:2], responses[1][:2])
            self.assertEqual(sorted(result[2] for result in responses), [False, True])
            self.assertEqual(self.counts(), (1, 1))
        finally:
            for worker in workers:
                if worker.is_alive():
                    worker.terminate()
                    worker.join()
            results.close()
            results.join_thread()

    def test_11_claves_distintas_son_cobros_distintos(self):
        first = call(self.url)
        second = call(self.url, key='otra-clave-001')
        self.assertNotEqual(json.loads(first[1])['cobro_id'], json.loads(second[1])['cobro_id'])
        self.assertEqual(self.counts(), (2, 2))

    def test_12_base_ocupada_responde_503_y_reintento_funciona(self):
        lock = self.store.connect()
        try:
            lock.execute('BEGIN IMMEDIATE')
            result = call(self.url)
            self.assertEqual(result[0], 503)
        finally:
            lock.rollback()
            lock.close()
        self.assertEqual(self.counts(), (0, 0))
        self.assertEqual(call(self.url)[0], 201)
        self.assertEqual(self.counts(), (1, 1))

    def test_13_map_ingenuo_demuestra_carrera(self):
        seen, charges = {}, []
        gate = threading.Barrier(2)
        def naive():
            if 'misma-clave' not in seen:
                gate.wait(timeout=5)  # Ambos ya pasaron el check.
                charges.append('cobro')
                seen['misma-clave'] = 'resultado'
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(naive) for _ in range(2)]
            for future in futures:
                future.result()
        self.assertEqual(len(charges), 2)


if __name__ == '__main__':
    if len(sys.argv) == 4 and sys.argv[1] == '--crash':
        def abrupt_exit(stage):
            if stage == sys.argv[3]:
                os._exit(77)  # Sin finally/rollback de Python: recupera SQLite.
        Store(sys.argv[2], delay=0).charge('clave-prueba-001', PAYLOAD, hook=abrupt_exit)
        sys.exit(1)
    unittest.main(verbosity=2)
