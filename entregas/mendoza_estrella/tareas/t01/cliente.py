import os
import time
import requests
import grpc

import servicio_pb2
import servicio_pb2_grpc

HOST_REST = os.environ.get("HOST_REST", "localhost")
HOST_GRPC = os.environ.get("HOST_GRPC", "localhost")


def probar_rest(item_id):
    url = f"http://{HOST_REST}:3000/items/{item_id}/"
    r = requests.get(url)
    print(f"[REST] GET /items/{item_id}/ -> {r.status_code} {r.json()}")


def probar_grpc(item_id):
    with grpc.insecure_channel(f"{HOST_GRPC}:50051") as channel:
        stub = servicio_pb2_grpc.ServicioItemsStub(channel)
        respuesta = stub.Buscar(servicio_pb2.SolicitudId(id=item_id))
        print(f"[gRPC] Buscar({item_id}) -> {respuesta}")


if __name__ == "__main__":
    time.sleep(3)
    for item_id in ["1", "2", "3", "99"]:
        probar_rest(item_id)
        probar_grpc(item_id)