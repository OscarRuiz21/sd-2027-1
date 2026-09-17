import time
import requests
import grpc

import service_pb2
import service_pb2_grpc

SERVER_HOST = "servidor"
REST_URL = f"http://{SERVER_HOST}:8000/items"
GRPC_HOST = f"{SERVER_HOST}:50051"

def test_rest(item_id):
    print(f"\n--- [REST] Consultando ID: {item_id} ---")
    try:
        res = requests.get(f"{REST_URL}/{item_id}")
        payload_bytes = len(res.content)
        header_bytes = sum(len(k) + len(v) for k, v in res.headers.items())
        print(f"Status HTTP: {res.status_code}")
        print(f"Respuesta: {res.text.strip()}")
        print(f"Bytes payload (JSON): {payload_bytes} bytes")
        print(f"Bytes totales aprox (Headers + Body): {header_bytes + payload_bytes} bytes")
    except Exception as e:
        print(f"Error en REST: {e}")

def test_grpc(item_id):
    print(f"\n--- [gRPC] Consultando ID: {item_id} ---")
    with grpc.insecure_channel(GRPC_HOST) as channel:
        stub = service_pb2_grpc.ItemServiceStub(channel)
        req = service_pb2.ItemRequest(id=item_id)
        try:
            res = stub.GetItem(req)
            binary_bytes = len(res.SerializeToString())
            print("Status gRPC: OK (StatusCode.OK)")
            print(f"Respuesta: id='{res.id}', name='{res.name}', description='{res.description}'")
            print(f"Bytes payload binario (Protobuf): {binary_bytes} bytes")
        except grpc.RpcError as e:
            print(f"Status gRPC: {e.code()}")
            print(f"Detalle: {e.details()}")

if __name__ == "__main__":
    time.sleep(3)
    print("================ INICIANDO PRUEBAS ================")
    test_rest("1")
    test_grpc("1")
    test_rest("99")
    test_grpc("99")
    print("\n================ FIN DE PRUEBAS ================")