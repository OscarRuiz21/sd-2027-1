"""Mini servicio de cobros idempotentes con SQLite y biblioteca estándar."""
from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

DB_PATH = Path(os.getenv("DB_PATH", "datos/cobros.db"))
PORT = int(os.getenv("PORT", "8080"))


def connection() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    return db


def initialize() -> None:
    with connection() as db:
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS idempotencia (
                clave TEXT PRIMARY KEY,
                hash_solicitud TEXT NOT NULL,
                estado TEXT NOT NULL CHECK (estado IN ('PROCESANDO', 'COMPLETADO')),
                respuesta TEXT,
                creado_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS cobros (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                clave_idempotencia TEXT NOT NULL UNIQUE,
                monto REAL NOT NULL CHECK (monto > 0),
                concepto TEXT NOT NULL,
                creado_en TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (clave_idempotencia) REFERENCES idempotencia(clave)
            );
            """
        )


def canonical_hash(body: dict) -> str:
    encoded = json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def charge(key: str, body: dict) -> tuple[int, dict]:
    amount = body.get("monto")
    concept = body.get("concepto")
    if not isinstance(amount, (int, float)) or amount <= 0 or not isinstance(concept, str) or not concept.strip():
        return 400, {"error": "Se requieren monto positivo y concepto"}

    request_hash = canonical_hash(body)
    db = connection()
    try:
        # BEGIN IMMEDIATE serializa a los escritores. La reserva de la clave y el
        # cobro quedan dentro de la misma transacción: ambos se confirman o ambos
        # se revierten si el proceso falla.
        db.execute("BEGIN IMMEDIATE")
        existing = db.execute(
            "SELECT hash_solicitud, estado, respuesta FROM idempotencia WHERE clave = ?",
            (key,),
        ).fetchone()

        if existing:
            if existing["hash_solicitud"] != request_hash:
                db.rollback()
                return 409, {"error": "La Idempotency-Key ya fue usada con otros datos"}
            if existing["estado"] == "COMPLETADO":
                response = json.loads(existing["respuesta"])
                response["repetida"] = True
                db.rollback()
                return 200, response
            db.rollback()
            return 409, {"error": "El cobro con esta clave todavía está procesándose"}

        # La PRIMARY KEY es la restricción UNIQUE que impide dos reservas.
        db.execute(
            "INSERT INTO idempotencia(clave, hash_solicitud, estado) VALUES (?, ?, 'PROCESANDO')",
            (key, request_hash),
        )

        # La pausa hace visible la carrera al lanzar dos peticiones simultáneas.
        time.sleep(0.4)
        cursor = db.execute(
            "INSERT INTO cobros(clave_idempotencia, monto, concepto) VALUES (?, ?, ?)",
            (key, float(amount), concept.strip()),
        )
        response = {
            "id": cursor.lastrowid,
            "idempotencyKey": key,
            "monto": float(amount),
            "concepto": concept.strip(),
            "estado": "COBRADO",
            "repetida": False,
        }
        db.execute(
            "UPDATE idempotencia SET estado = 'COMPLETADO', respuesta = ? WHERE clave = ?",
            (json.dumps(response, ensure_ascii=False), key),
        )
        db.commit()
        return 201, response
    except sqlite3.IntegrityError:
        db.rollback()
        return 409, {"error": "La clave o el cobro ya existen"}
    finally:
        db.close()


class Handler(BaseHTTPRequestHandler):
    server_version = "CobrosIdempotentes/1.0"

    def send_json(self, status: int, body: object) -> None:
        payload = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self) -> None:
        if self.path == "/health":
            self.send_json(200, {"status": "UP"})
            return
        if self.path == "/cobros":
            with connection() as db:
                rows = [dict(row) for row in db.execute(
                    "SELECT id, clave_idempotencia, monto, concepto, creado_en FROM cobros ORDER BY id"
                )]
            self.send_json(200, rows)
            return
        self.send_json(404, {"error": "Ruta inexistente"})

    def do_POST(self) -> None:
        if self.path != "/cobros":
            self.send_json(404, {"error": "Ruta inexistente"})
            return
        key = self.headers.get("Idempotency-Key", "").strip()
        if not key:
            self.send_json(400, {"error": "Falta el header Idempotency-Key"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length))
        except (ValueError, json.JSONDecodeError):
            self.send_json(400, {"error": "JSON inválido"})
            return
        status, response = charge(key, body)
        self.send_json(status, response)

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"{self.address_string()} - {fmt % args}")


if __name__ == "__main__":
    initialize()
    print(f"Servicio de cobros escuchando en http://0.0.0.0:{PORT}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
