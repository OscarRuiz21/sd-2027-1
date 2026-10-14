import grpc
import requests
import service_pb2
import service_pb2_grpc

def test_rest(item_id: str):
    print(f"\n--- Probando Interfaz REST para el ID: {item_id} ---")
    url = f"http://localhost:8000/items/{item_id}"
    try:
        response = requests.get(url)
        if response.status_code == 200:
            print("Respuesta REST exitosa:", response.json())
        else:
            print(f"Error REST ({response.status_code}):", response.json())
    except Exception as e:
        print("No se pudo conectar al servidor REST:", e)

def test_grpc(item_id: str):
    print(f"\n--- Probando Interfaz gRPC para el ID: {item_id} ---")
    # Conectar al servidor gRPC
    with grpc.insecure_channel('localhost:50051') as channel:
        stub = service_pb2_grpc.ItemServiceStub(channel)
        try:
            request = service_pb2.ItemRequest(id=item_id)
            response = stub.GetItem(request)
            print("Respuesta gRPC exitosa:")
            print(f"  ID: {response.id}")
            print(f"  Nombre: {response.name}")
            print(f"  Descripción: {response.description}")
            print(f"  Precio: {response.price}")
        except grpc.RpcError as e:
            print(f"Error gRPC ({e.code().name}): {e.details()}")

if __name__ == "__main__":
    # Probamos con un ID existente (ej: "1") y uno que no existe (ej: "99")
    for test_id in ["1", "99"]:
        test_rest(test_id)
        test_grpc(test_id)