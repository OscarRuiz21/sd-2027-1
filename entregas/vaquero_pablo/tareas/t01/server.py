"""Un proceso, dos controladores y una sola instancia de CatalogService."""
import json
import signal
import threading
from concurrent import futures
from dataclasses import asdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

import grpc
import catalog_pb2
import catalog_pb2_grpc
from service import CatalogService, InvalidId, ProductNotFound


def rest_handler(service):
    class RestController(BaseHTTPRequestHandler):
        def respond(self, status, data):
            body = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path = urlsplit(self.path).path
            parts = path.strip("/").split("/")
            if len(parts) != 2 or parts[0] != "products":
                self.respond(404, {"error": "Ruta no encontrada"})
                return
            try:
                try:
                    product_id = int(parts[1])
                except ValueError:
                    product_id = None
                product = service.get_product(product_id)
                self.respond(200, asdict(product))
            except InvalidId as error:
                self.respond(400, {"error": str(error)})
            except ProductNotFound as error:
                self.respond(404, {"error": str(error)})

    return RestController


class GrpcController(catalog_pb2_grpc.CatalogServicer):
    def __init__(self, service):
        self.service = service

    def GetProduct(self, request, context):
        try:
            product = self.service.get_product(request.id)
            return catalog_pb2.Product(**asdict(product))
        except InvalidId as error:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(error))
        except ProductNotFound as error:
            context.abort(grpc.StatusCode.NOT_FOUND, str(error))


class Server:
    def __init__(self, host="0.0.0.0", rest_port=8000, grpc_port=50051, service=None):
        self.service = service if service is not None else CatalogService()
        self.http = ThreadingHTTPServer((host, rest_port), rest_handler(self.service))
        self.grpc = grpc.server(futures.ThreadPoolExecutor(max_workers=8))
        catalog_pb2_grpc.add_CatalogServicer_to_server(GrpcController(self.service), self.grpc)
        self.grpc_port = self.grpc.add_insecure_port(f"{host}:{grpc_port}")
        if self.grpc_port == 0:
            self.http.server_close()
            raise RuntimeError("No se pudo abrir el puerto gRPC")
        self.rest_port = self.http.server_port
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True)

    def start(self):
        self.grpc.start()
        self.thread.start()

    def stop(self):
        self.grpc.stop(grace=2).wait()
        self.http.shutdown()
        self.http.server_close()
        self.thread.join()


def main():
    stopped = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stopped.set())
    server = Server()
    server.start()
    print(f"Una instancia de CatalogService; REST :{server.rest_port}; gRPC :{server.grpc_port}", flush=True)
    try:
        stopped.wait()
    finally:
        server.stop()


if __name__ == "__main__":
    main()
