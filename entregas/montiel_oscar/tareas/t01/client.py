"""Cliente que consulta el mismo artículo mediante REST y gRPC."""

import sys
import time

import grpc
import requests

import catalog_pb2
import catalog_pb2_grpc


REST_URL = "http://server:8000"
GRPC_TARGET = "server:50051"


def call_rest(item_id: str) -> None:
    """Consulta REST, imprime la respuesta y mide sus mensajes de aplicación."""
    url = f"{REST_URL}/items/{item_id}"

    for attempt in range(10):
        try:
            response = requests.get(url, timeout=3)
            response.raise_for_status()
            break
        except requests.RequestException:
            if attempt == 9:
                raise
            time.sleep(1)

    request_line = f"GET /items/{item_id} HTTP/1.1\r\n".encode("utf-8")

    print("=== REST ===")
    print(f"Respuesta: {response.json()}")
    print(f"Bytes de línea de petición REST: {len(request_line)}")
    print(f"Bytes de cuerpo JSON REST: {len(response.content)}")


def call_grpc(item_id: str) -> None:
    """Consulta gRPC, imprime la respuesta y mide los mensajes protobuf."""
    channel = grpc.insecure_channel(GRPC_TARGET)

    for attempt in range(10):
        try:
            grpc.channel_ready_future(channel).result(timeout=3)
            break
        except grpc.FutureTimeoutError:
            if attempt == 9:
                raise
            time.sleep(1)

    request = catalog_pb2.ItemRequest(id=item_id)
    stub = catalog_pb2_grpc.CatalogStub(channel)
    try:
        reply = stub.GetItem(request)
    except grpc.RpcError as error:
        print("=== gRPC ===")
        print(f"Error gRPC: {error.code().name}: {error.details()}")
        channel.close()
        return

    print("=== gRPC ===")
    print(
        "Respuesta: "
        f"{{'id': '{reply.id}', 'name': '{reply.name}', 'price': {reply.price}}}"
    )
    print(f"Bytes de petición protobuf: {len(request.SerializeToString())}")
    print(f"Bytes de respuesta protobuf: {len(reply.SerializeToString())}")

    channel.close()


def main() -> None:
    """Ejecuta REST, gRPC o ambos según el primer argumento."""
    transport = sys.argv[1] if len(sys.argv) > 1 else "both"
    item_id = sys.argv[2] if len(sys.argv) > 2 else "1"

    if transport in {"rest", "both"}:
        call_rest(item_id)

    if transport in {"grpc", "both"}:
        call_grpc(item_id)


if __name__ == "__main__":
    main()
