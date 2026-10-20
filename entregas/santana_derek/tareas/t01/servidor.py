import json
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

import grpc
import catalogo_pb2 as pb
import catalogo_pb2_grpc as rpc
from logica import consultar

class REST(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            prefix, value = self.path.rsplit("/", 1)
            if prefix != "/productos":
                raise KeyError()
            result, status = consultar(int(value)), 200
        except (ValueError, KeyError):
            result, status = {"error": "Producto inexistente"}, 404
        body = json.dumps(result, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

class GRPC(rpc.CatalogoServicer):
    def Consultar(self, request, context):
        try:
            return pb.Producto(**consultar(request.id))
        except KeyError:
            context.abort(grpc.StatusCode.NOT_FOUND, "Producto inexistente")

if __name__ == "__main__":
    server = grpc.server(ThreadPoolExecutor(max_workers=4))
    rpc.add_CatalogoServicer_to_server(GRPC(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    http = ThreadingHTTPServer(("0.0.0.0", 8080), REST)
    Thread(target=http.serve_forever, daemon=True).start()
    print("Un proceso; REST :8080 y gRPC :50051; una sola lógica", flush=True)
    try:
        server.wait_for_termination()
    finally:
        http.shutdown()
        server.stop(0)
