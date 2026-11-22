import requests
import grpc
import service_pb2
import service_pb2_grpc
import time

def probar_rest(usuario_id):
    url = f"http://servidor:8000/usuarios/{usuario_id}"
    try:
        response = requests.get(url)
        bytes_recibidos = len(response.content)
        print(f"[REST] Status: {response.status_code} | Respuesta: {response.text} | Payload: {bytes_recibidos} B")
    except Exception as e:
        print(f"[REST] Error: {e}")

def probar_grpc(usuario_id):
    channel = grpc.insecure_channel("servidor:50051")
    stub = service_pb2_grpc.UsuarioServiceStub(channel)
    request = service_pb2.UsuarioRequest(id=usuario_id)
    try:
        response = stub.ObtenerUsuario(request)
        bytes_res = response.ByteSize()
        print(f"[gRPC] Encontrado: {response.encontrado} | Nombre: {response.nombre} | Payload pb: {bytes_res} B")
    except Exception as e:
        print(f"[gRPC] Error: {e}")

if __name__ == "__main__":
    time.sleep(3)
    print("=== Probando ID 1 (Existe) ===")
    probar_rest(1)
    probar_grpc(1)

    print("\n=== Probando ID 99 (No existe) ===")
    probar_rest(99)
    probar_grpc(99)