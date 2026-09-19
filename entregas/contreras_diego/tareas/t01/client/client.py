import time
import requests
import grpc
import service_pb2
import service_pb2_grpc

def call_rest(user_id):
    url = f"http://server:8000/users/{user_id}"
    res = requests.get(url)
    print(f"[REST] Status: {res.status_code} | Payload recibido: {len(res.content)} bytes | Respuesta: {res.text}")

def call_grpc(user_id):
    with grpc.insecure_channel("server:50051") as channel:
        stub = service_pb2_grpc.UserServiceStub(channel)
        req = service_pb2.UserRequest(id=user_id)
        res = stub.GetUser(req)
        payload_size = res.ByteSize()
        print(f"[gRPC] Status: OK | Payload respuesta: {payload_size} bytes | Encontrado: {res.found}, Nombre: {res.name}")

if __name__ == "__main__":
    time.sleep(2) # pausa para que elservidor se levante
    print("=== PROBANDO REST ===")
    call_rest(1)
    call_rest(99) # ID inexistente

    print("\n=== PROBANDO gRPC ===")
    call_grpc(1)
    call_grpc(99) # ID inexistente