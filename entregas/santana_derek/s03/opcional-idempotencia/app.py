"""Cobros simulados: sólo biblioteca estándar de Python, sin proveedor externo."""

import json
import os
import re
import sqlite3
import time
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


SCHEMA = """
CREATE TABLE IF NOT EXISTS operaciones (
    id INTEGER PRIMARY KEY,
    clave TEXT NOT NULL UNIQUE,
    solicitud TEXT NOT NULL,
    creada_en TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('reservada', 'completada')),
    codigo_http INTEGER,
    respuesta TEXT
);
CREATE TABLE IF NOT EXISTS cobros (
    id TEXT PRIMARY KEY,
    operacion_id INTEGER NOT NULL UNIQUE REFERENCES operaciones(id),
    monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0),
    moneda TEXT NOT NULL CHECK (moneda = 'MXN'),
    referencia TEXT NOT NULL
);
"""


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def validate(key, payload):
    if not isinstance(key, str) or not re.fullmatch(r'[A-Za-z0-9_-]{8,128}', key):
        raise ValueError('Idempotency-Key: 8 a 128 letras ASCII, números, guiones o guiones bajos.')
    if not isinstance(payload, dict) or set(payload) != {'monto_centavos', 'moneda', 'referencia'}:
        raise ValueError('Se requieren exactamente monto_centavos, moneda y referencia.')
    amount = payload['monto_centavos']
    if type(amount) is not int or not 1 <= amount <= 100_000_000:
        raise ValueError('monto_centavos debe ser un entero entre 1 y 100000000.')
    if payload['moneda'] != 'MXN':
        raise ValueError('La moneda admitida es MXN.')
    reference = payload['referencia']
    if not isinstance(reference, str) or not reference.strip() or len(reference) > 100:
        raise ValueError('referencia debe ser texto no vacío de hasta 100 caracteres.')
    # Rechaza sustitutos Unicode aislados antes de persistir o enviar UTF-8.
    encode(payload).encode('utf-8')
    return encode(payload)


class Store:
    def __init__(self, path, delay=0.2):
        self.path = str(path)
        self.delay = delay
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        db = self.connect()
        try:
            db.execute('PRAGMA journal_mode=WAL')
            db.executescript(SCHEMA)
        finally:
            db.close()

    def connect(self):
        db = sqlite3.connect(self.path, timeout=5, isolation_level=None)
        db.execute('PRAGMA foreign_keys=ON')
        db.execute('PRAGMA synchronous=FULL')
        return db

    def charge(self, key, payload, hook=None):
        """hook sólo se inyecta desde pruebas; no existe un endpoint para fallos."""
        request = validate(key, payload)
        db = self.connect()
        try:
            # SQLite serializa escritores, incluso entre procesos con el mismo archivo.
            db.execute('BEGIN IMMEDIATE')
            try:
                operation = db.execute(
                    'INSERT INTO operaciones (clave, solicitud, creada_en, estado) '
                    "VALUES (?, ?, ?, 'reservada')",
                    (key, request, datetime.now(timezone.utc).isoformat()),
                ).lastrowid
            except sqlite3.IntegrityError:
                previous = db.execute(
                    'SELECT solicitud, codigo_http, respuesta FROM operaciones WHERE clave=?',
                    (key,),
                ).fetchone()
                if previous is None:
                    raise
                db.rollback()
                if previous[0] != request:
                    return 409, encode({'error': 'La clave ya fue utilizada con otros datos.'}), False
                return previous[1], previous[2], True

            if hook:
                hook('after_insert')
            time.sleep(self.delay)  # Hace visible la contención en el ejemplo didáctico.
            charge_id = str(uuid.uuid4())
            # El efecto de negocio ES este INSERT local; no se llama a un banco.
            db.execute(
                'INSERT INTO cobros VALUES (?, ?, ?, ?, ?)',
                (charge_id, operation, payload['monto_centavos'], payload['moneda'], payload['referencia']),
            )
            if hook:
                hook('after_charge')
            response = encode({
                'cobro_id': charge_id, 'estado': 'simulado',
                'monto_centavos': payload['monto_centavos'],
                'moneda': payload['moneda'], 'referencia': payload['referencia'],
            })
            db.execute(
                "UPDATE operaciones SET estado='completada', codigo_http=201, respuesta=? WHERE id=?",
                (response, operation),
            )
            db.commit()
            if hook:
                hook('after_commit')
            return 201, response, False
        finally:
            if db.in_transaction:
                db.rollback()
            db.close()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('JSON con campos repetidos.')
        result[key] = value
    return result


def handler_for(store):
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def reply(self, status, body, replay=None):
            data = body.encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            if replay is not None:
                self.send_header('Idempotency-Replayed', str(replay).lower())
            if status == 503:
                self.send_header('Retry-After', '1')
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            if self.path == '/salud':
                self.reply(200, encode({'estado': 'ok'}))
            else:
                self.reply(404, encode({'error': 'Ruta inexistente.'}))

        def do_POST(self):
            if self.path != '/cobros':
                self.reply(404, encode({'error': 'Ruta inexistente.'}))
                return
            try:
                keys = self.headers.get_all('Idempotency-Key', [])
                if len(keys) != 1:
                    raise ValueError('Envía exactamente un header Idempotency-Key.')
                if self.headers.get_content_type() != 'application/json':
                    self.reply(415, encode({'error': 'Usa Content-Type: application/json.'}))
                    return
                lengths = self.headers.get_all('Content-Length', [])
                if self.headers.get('Transfer-Encoding') or len(lengths) != 1:
                    raise ValueError('Envía Content-Length único; no se admite Transfer-Encoding.')
                if not re.fullmatch(r'[0-9]+', lengths[0]):
                    raise ValueError('Content-Length inválido.')
                length = int(lengths[0])
                if not 1 <= length <= 4096:
                    self.reply(413, encode({'error': 'El cuerpo debe tener entre 1 y 4096 bytes.'}))
                    return
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise ValueError('Cuerpo incompleto.')
                payload = json.loads(raw.decode('utf-8'), object_pairs_hook=unique_object)
                status, body, replay = store.charge(keys[0], payload)
                self.reply(status, body, replay)
            except (ValueError, UnicodeError, RecursionError):
                self.reply(400, encode({'error': 'Solicitud inválida. Revisa clave y campos del JSON.'}))
            except TimeoutError:
                self.reply(408, encode({'error': 'Tiempo agotado al recibir el cuerpo.'}))
            except sqlite3.OperationalError as error:
                if 'locked' in str(error) or 'busy' in str(error):
                    self.reply(503, encode({'error': 'Base ocupada; reintenta con la misma clave.'}))
                else:
                    self.log_error('Fallo SQLite: %s', error)
                    self.reply(500, encode({'error': 'No se pudo guardar la operación.'}))
    return Handler


def main():
    store = Store(os.environ.get('DB_PATH', 'data/cobros.sqlite3'))
    server = ThreadingHTTPServer(
        (os.environ.get('HOST', '127.0.0.1'), int(os.environ.get('PORT', '8080'))),
        handler_for(store),
    )
    print('Cobros SIMULADOS disponibles en el puerto', server.server_port, flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
