import os

import grpc
import requests

import products_pb2
import products_pb2_grpc

REST_URL = os.getenv("REST_URL", "http://localhost:8000")
GRPC_TARGET = os.getenv("GRPC_TARGET", "localhost:50051")


def via_rest(product_id):
    resp = requests.get(f"{REST_URL}/products/{product_id}")
    if resp.status_code == 404:
        return "no hay datos (HTTP 404)"
    return resp.json()


def via_grpc(stub, product_id):
    try:
        resp = stub.GetProduct(
            products_pb2.ProductRequest(product_id=product_id))
        return {"name": resp.name, "value": resp.value, "stock": resp.stock}
    except grpc.RpcError as e:
        if e.code() == grpc.StatusCode.NOT_FOUND:
            return "no hay datos (gRPC NOT_FOUND)"
        raise


if __name__ == "__main__":
    stub = products_pb2_grpc.ProductsStub(grpc.insecure_channel(GRPC_TARGET))
    for product_id in [1, 2, 3, 5]:
        print(f"--- id={product_id} ---")
        print("  REST:", via_rest(product_id))
        print("  gRPC:", via_grpc(stub, product_id))
