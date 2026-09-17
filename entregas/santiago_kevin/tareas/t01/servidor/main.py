from concurrent import futures

import grpc
import uvicorn

import products_pb2_grpc
from grpc_controlador import ProductsServicer
from rest_controlador import app


def start_grpc(port=50051):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    products_pb2_grpc.add_ProductsServicer_to_server(
        ProductsServicer(), server)
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"gRPC escuchando en puerto {port}")
    return server


if __name__ == "__main__":
    grpc_server = start_grpc()
    uvicorn.run(app, host="0.0.0.0", port=8000)
    grpc_server.stop(0)
