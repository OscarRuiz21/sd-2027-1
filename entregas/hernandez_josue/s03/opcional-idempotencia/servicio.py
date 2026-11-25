import argparse
from contextlib import closing
from functools import partial
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import sqlite3


def conectar(ruta):
    db = sqlite3.connect(ruta, timeout=5, isolation_level="IMMEDIATE")
    db.execute("PRAGMA foreign_keys = ON")
    return db


def registrar_cobro(ruta, clave, monto):
    with closing(conectar(ruta)) as db, db:
        reserva = db.execute(
            """INSERT INTO solicitudes (clave, monto_centavos) VALUES (?, ?)
               ON CONFLICT (clave) DO NOTHING""",
            (clave, monto),
        )
        if reserva.rowcount == 1:
            cobro = db.execute("INSERT INTO cobros (clave) VALUES (?)", (clave,))
            return 201, {"cobro_id": cobro.lastrowid, "monto_centavos": monto}

        cobro_id, monto_guardado = db.execute(
            """SELECT c.id, s.monto_centavos
               FROM solicitudes s JOIN cobros c ON c.clave = s.clave
               WHERE s.clave = ?""",
            (clave,),
        ).fetchone()
        if monto != monto_guardado:
            return 409, {"error": "La clave ya se usó para un monto distinto"}
        return 200, {"cobro_id": cobro_id, "monto_centavos": monto_guardado}


class Cobros(BaseHTTPRequestHandler):
    def __init__(self, *args, ruta, **kwargs):
        self.ruta = ruta
        super().__init__(*args, **kwargs)

    def do_POST(self):
        if self.path != "/cobros":
            self.responder(404, {"error": "Ruta desconocida"})
            return

        clave = self.headers.get("Idempotency-Key", "").strip()
        if not 1 <= len(clave) <= 128:
            self.responder(400, {"error": "Envía un Idempotency-Key de 1 a 128 caracteres"})
            return

        try:
            longitud = int(self.headers.get("Content-Length", "0"))
            if not 1 <= longitud <= 4096:
                raise ValueError
            datos = json.loads(self.rfile.read(longitud))
            monto = datos.get("monto_centavos") if isinstance(datos, dict) else None
            if type(monto) is not int or not 1 <= monto <= (2**63 - 1):
                raise ValueError
        except (ValueError, RecursionError):
            self.responder(400, {"error": "Envía monto_centavos como entero positivo"})
            return

        try:
            estado, cuerpo = registrar_cobro(self.ruta, clave, monto)
        except sqlite3.Error as error:
            self.log_error("SQLite: %s", error)
            self.responder(503, {"error": "Base de datos no disponible"})
            return
        self.responder(estado, cuerpo)

    def responder(self, estado, datos):
        cuerpo = json.dumps(datos, ensure_ascii=False).encode("utf-8")
        self.send_response(estado)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(cuerpo)))
        self.end_headers()
        self.wfile.write(cuerpo)


def crear_servidor(ruta, puerto=8000):
    with closing(conectar(ruta)) as db:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS solicitudes (
                clave TEXT NOT NULL UNIQUE,
                monto_centavos INTEGER NOT NULL CHECK (monto_centavos > 0)
            );
            CREATE TABLE IF NOT EXISTS cobros (
                id INTEGER PRIMARY KEY,
                clave TEXT NOT NULL UNIQUE REFERENCES solicitudes(clave)
            );
        """)
    return ThreadingHTTPServer(("127.0.0.1", puerto), partial(Cobros, ruta=ruta))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", type=Path, default=Path("cobros.sqlite3"))
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    with crear_servidor(args.db, args.port) as servidor:
        print(f"Escuchando en http://127.0.0.1:{servidor.server_port}", flush=True)
        try:
            servidor.serve_forever()
        except KeyboardInterrupt:
            pass
