import sys

import grpc
import requests

from proto import data_pb2
from proto import data_pb2_grpc


def consultar_rest(id):
    response = requests.get(
        f"http://server:5000/data/{id}"
    )

    print("=== REST ===")
    print(f"HTTP {response.status_code}")
    print(response.json())


def consultar_grpc(id):
    channel = grpc.insecure_channel(
        "server:50051"
    )

    stub = data_pb2_grpc.DataServiceStub(channel)

    request = data_pb2.GetDataRequest(id=id)

    response = stub.GetData(request)

    print("=== gRPC ===")
    print(f"found: {response.found}")
    print(f"id: {response.id}")
    print(f"nombre: {response.nombre}")
    print(f"carrera: {response.carrera}")

if __name__ == "__main__":
    id = int(sys.argv[1])

    consultar_rest(id)
    consultar_grpc(id)
