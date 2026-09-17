# client/client.py

import os
import sys
import requests
import grpc

import item_pb2
import item_pb2_grpc

REST_HOST = os.environ.get("REST_HOST", "127.0.0.1")
REST_PORT = os.environ.get("REST_PORT", "8080")
GRPC_HOST = os.environ.get("GRPC_HOST", "127.0.0.1")
GRPC_PORT = os.environ.get("GRPC_PORT", "50051")


def call_rest(item_id):
    url = f"http://{REST_HOST}:{REST_PORT}/items/{item_id}"
    response = requests.get(url)

    print(f"\n[REST] GET {url}")
    print(f"  status: {response.status_code}")
    print(f"  bytes body: {len(response.content)}")
    print(f"  body: {response.text}")


def call_grpc(item_id):
    channel = grpc.insecure_channel(f"{GRPC_HOST}:{GRPC_PORT}")
    stub = item_pb2_grpc.ItemServiceStub(channel)

    request = item_pb2.ItemRequest(id=item_id)
    response = stub.GetItem(request)

    print(f"\n[gRPC] GetItem({item_id})")
    print(f"  bytes request: {request.ByteSize()}")
    print(f"  bytes response: {response.ByteSize()}")
    print(f"  respuesta: {response}")


if __name__ == "__main__":
    protocolo = sys.argv[1]
    item_id = sys.argv[2]

    if protocolo in ("rest", "ambos"):
        call_rest(item_id)
    if protocolo in ("grpc", "ambos"):
        call_grpc(item_id)