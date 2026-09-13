import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

from catalog import get_product


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    disable_nagle_algorithm = True

    def reply(self, status, body):
        data = json.dumps(body, separators=(",", ":"), ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        if status == 405:
            self.send_header("Allow", "GET")
            self.send_header("Connection", "close")
            self.close_connection = True
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        match = re.fullmatch(r"/products/([^/]+)", urlsplit(self.path).path)
        if not match:
            self.reply(404, {"error": "Ruta no encontrada"})
            return
        raw = match.group(1)
        if not re.fullmatch(r"-?[0-9]{1,10}", raw):
            self.reply(400, {"error": "El ID debe ser un entero de 32 bits positivo"})
            return
        try:
            self.reply(200, get_product(int(raw)))
        except ValueError as error:
            self.reply(400, {"error": str(error)})
        except KeyError as error:
            self.reply(404, {"error": error.args[0]})

    def method_not_allowed(self):
        self.reply(405, {"error": "Solo se permite GET"})

    do_POST = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = method_not_allowed

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    print("REST escuchando en 0.0.0.0:8080", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
