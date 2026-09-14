import os
import time
import requests
import grpc

import servicio_pb2
import servicio_pb2_grpc

SERVER_HOST = os.getenv("SERVER_HOST", "servidor")
REST_URL = f"http://{SERVER_HOST}:8000/items"
GRPC_TARGET = f"{SERVER_HOST}:50051"

def consultar_rest(item_id: str):
    print(f"\n--- [REST] Consultando ID: {item_id} ---")
    try:
        resp = requests.get(f"{REST_URL}/{item_id}")
        payload_size = len(resp.content)
        headers_size = len(str(resp.headers))
        print(f"Status Code: {resp.status_code}")
        print(f"Respuesta: {resp.text.strip()}")
        print(f"Bytes en payload: {payload_size} bytes (Encabezados: ~{headers_size} bytes)")
    except Exception as e:
        print(f"Error REST: {e}")

def consultar_grpc(item_id: str):
    print(f"\n--- [gRPC] Consultando ID: {item_id} ---")
    channel = grpc.insecure_channel(GRPC_TARGET)
    stub = servicio_pb2_grpc.ItemServiceStub(channel)
    request = servicio_pb2.ItemRequest(id=item_id)
    try:
        response = stub.GetItem(request)
        serialized_size = response.ByteSize()
        print(f"Respuesta gRPC: id='{response.id}', nombre='{response.nombre}', detalle='{response.detalle}'")
        print(f"Bytes en payload Protobuf: {serialized_size} bytes")
    except grpc.RpcError as e:
        print(f"gRPC Status: {e.code()} | Detalle: {e.details()}")

if __name__ == "__main__":
    time.sleep(3)
    print("========================================")
    print(" INICIANDO PRUEBAS DE COMUNICACION ")
    print("========================================")
    consultar_rest("1")
    consultar_grpc("1")
    consultar_rest("99")
    consultar_grpc("99")
    print("\n========================================")
    print(" PRUEBAS FINALIZADAS CON EXITO ")
    print("========================================")
