"""Mini servicio didáctico: un cobro es una fila local, no un cargo bancario."""

import argparse
import hashlib
import json
import logging
from pathlib import Path
import re
import sqlite3
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parent


def encode(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


class APIError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def connect(db_path, timeout=5):
    conn = sqlite3.connect(db_path, timeout=timeout, isolation_level=None)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA synchronous = FULL")
    return conn


def init_db(db_path):
    conn = connect(db_path)
    try:
        conn.execute("PRAGMA journal_mode = WAL")
        conn.executescript((ROOT / "schema.sql").read_text(encoding="utf-8"))
    finally:
        conn.close()


def validate(key, payload):
    if not isinstance(key, str) or not re.fullmatch(r"[A-Za-z0-9._:-]{1,128}", key):
        raise APIError(400, "Envía una sola Idempotency-Key de 1 a 128 caracteres: letras, números, . _ : -")
    if not isinstance(payload, dict) or set(payload) != {"amount", "currency"}:
        raise APIError(422, "El JSON debe contener únicamente amount y currency")
    if type(payload["amount"]) is not int or not 1 <= payload["amount"] <= 100_000_000:
        raise APIError(422, "amount debe ser un entero de 1 a 100000000, expresado en centavos")
    if payload["currency"] not in ("MXN", "USD", "EUR"):
        raise APIError(422, "currency debe ser MXN, USD o EUR")


def charge(db_path, key, payload, delay=0, timeout=5):
    """Devuelve (status, bytes del cuerpo, replay). Cada llamada abre su conexión."""
    validate(key, payload)
    fingerprint = hashlib.sha256(encode(payload)).hexdigest()
    conn = connect(db_path, timeout)
    try:
        conn.execute("BEGIN")
        try:
            # No hay SELECT previo: el INSERT con UNIQUE decide quién puede cobrar.
            conn.execute(
                "INSERT INTO idempotency_keys (key, request_hash) VALUES (?, ?)",
                (key, fingerprint),
            )
        except sqlite3.IntegrityError:
            conn.rollback()
            original = conn.execute(
                "SELECT request_hash, response_status, response_body "
                "FROM idempotency_keys WHERE key = ?", (key,),
            ).fetchone()
            if original is None:
                raise  # No ocultar una restricción distinta de la llave duplicada.
            old_hash, status, body = original
            if old_hash != fingerprint:
                raise APIError(409, "Esta Idempotency-Key ya se usó con otros datos")
            if status is None or body is None:
                raise RuntimeError("Registro incompleto: se violó la transacción del servicio")
            return status, body, True

        # Amplía la ventana de concurrencia en la demostración.
        if delay:
            time.sleep(delay)
        result = {"id": str(uuid.uuid4()), "status": "succeeded", **payload}
        conn.execute(
            "INSERT INTO charges (id, idempotency_key, amount, currency) VALUES (?, ?, ?, ?)",
            (result["id"], key, payload["amount"], payload["currency"]),
        )
        body = encode(result)
        conn.execute(
            "UPDATE idempotency_keys SET response_status = 201, response_body = ? WHERE key = ?",
            (body, key),
        )
        # Llave, efecto local y respuesta se confirman juntos, antes de responder HTTP.
        conn.commit()
        return 201, body, False
    except sqlite3.OperationalError as exc:
        if getattr(exc, "sqlite_errorcode", 0) & 0xFF == sqlite3.SQLITE_BUSY:
            raise APIError(503, "Base ocupada; reintenta con la misma llave y los mismos datos") from exc
        raise
    finally:
        if conn.in_transaction:
            conn.rollback()
        conn.close()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            if self.path != "/charges":
                raise APIError(404, "Ruta inexistente; usa POST /charges")
            keys = self.headers.get_all("Idempotency-Key", [])
            if len(keys) != 1:
                raise APIError(400, "Envía exactamente un header Idempotency-Key")
            media_type = self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
            if media_type != "application/json":
                raise APIError(415, "Usa Content-Type: application/json")
            lengths = self.headers.get_all("Content-Length", [])
            if self.headers.get("Transfer-Encoding") or len(lengths) != 1:
                raise APIError(400, "Envía un Content-Length único; no se admite Transfer-Encoding")
            try:
                size = int(lengths[0])
            except ValueError:
                raise APIError(400, "Content-Length inválido")
            if not 0 < size <= 4096:
                raise APIError(413, "El cuerpo debe tener entre 1 y 4096 bytes")
            try:
                payload = json.loads(self.rfile.read(size).decode("utf-8"))
            except (ValueError, UnicodeError):
                raise APIError(400, "JSON inválido")
            status, body, replay = charge(
                self.server.db_path, keys[0], payload,
                delay=self.server.delay, timeout=self.server.lock_timeout,
            )
            self.respond(status, body, replay)
        except APIError as exc:
            self.respond(exc.status, encode({"error": str(exc)}))
        except (BrokenPipeError, ConnectionResetError):
            # Si el cliente perdió la respuesta después del COMMIT, podrá recuperarla.
            pass
        except Exception:
            logging.exception("Error al atender el cobro")
            self.respond(500, encode({"error": "Error interno; reintenta con la misma llave"}))

    def respond(self, status, body, replay=None):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if replay is not None:
            self.send_header("Idempotency-Replayed", str(replay).lower())
        if status == 503:
            self.send_header("Retry-After", "1")
        self.end_headers()
        self.wfile.write(body)


def make_server(db_path, port=8000, delay=0, timeout=5):
    init_db(db_path)
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    server.db_path = db_path
    server.delay = delay
    server.lock_timeout = timeout
    return server


def nonnegative(value):
    number = float(value)
    if not 0 <= number < float("inf"):
        raise argparse.ArgumentTypeError("Debe ser un número finito no negativo")
    return number


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", type=Path, default=ROOT / "charges.sqlite3")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--delay", type=nonnegative, default=0, help="Segundos de demora para probar concurrencia")
    parser.add_argument("--lock-timeout", type=nonnegative, default=5)
    args = parser.parse_args()
    server = make_server(args.db, args.port, args.delay, args.lock_timeout)
    print(f"Servidor: http://127.0.0.1:{server.server_port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
