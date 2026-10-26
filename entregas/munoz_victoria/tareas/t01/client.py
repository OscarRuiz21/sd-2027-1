
import os

import grpc
import requests
import service_pb2
import service_pb2_grpc

SERVICE_HOST = os.getenv("SERVICE_HOST", "localhost")
REST_PORT = os.getenv("REST_PORT", "8000")
GRPC_PORT = os.getenv("GRPC_PORT", "50051")


def test_rest(item_id: str):
    print(f"\n--- Probando Interfaz REST para el ID: {item_id} ---")
    url = f"http://{SERVICE_HOST}:{REST_PORT}/items/{item_id}"

    try:
        response = requests.get(url, timeout=5)
        print(f"Tamaño de respuesta REST: {len(response.content)} bytes")

        if response.status_code == 200:
            print("Respuesta REST exitosa:", response.json())
        else:
            print(f"Error REST ({response.status_code}):", response.json())

    except requests.RequestException as e:
        print("No se pudo conectar al servidor REST:", e)


def test_grpc(item_id: str):
    print(f"\n--- Probando Interfaz gRPC para el ID: {item_id} ---")

    try:
        with grpc.insecure_channel(
            f"{SERVICE_HOST}:{GRPC_PORT}"
        ) as channel:
            stub = service_pb2_grpc.ItemServiceStub(channel)
            request = service_pb2.ItemRequest(id=item_id)
            response = stub.GetItem(request, timeout=5)

            print(f"Tamaño de respuesta gRPC: {response.ByteSize()} bytes")
            print("Respuesta gRPC exitosa:")
            print(f"  ID: {response.id}")
            print(f"  Nombre: {response.name}")
            print(f"  Descripción: {response.description}")
            print(f"  Precio: {response.price}")

    except grpc.RpcError as e:
        print(f"Error gRPC ({e.code().name}): {e.details()}")


if __name__ == "__main__":
    for test_id in ["1", "99"]:
        test_rest(test_id)
        test_grpc(test_id)
