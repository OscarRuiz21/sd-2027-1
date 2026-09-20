import sys
import requests
import grpc
import item_pb2
import item_pb2_grpc

def llamar_rest(id, host="localhost"):
    url = f"http://{host}:3000/item/{id}"
    respuesta = requests.get(url)
    print("REST responde:", respuesta.json())

def llamar_grpc(id, host="localhost"):
    canal = grpc.insecure_channel(f"{host}:50051")
    stub = item_pb2_grpc.ItemServiceStub(canal)
    respuesta = stub.GetItem(item_pb2.ItemRequest(id=id))
    print("gRPC responde:", respuesta)

if __name__ == "__main__":
    id = sys.argv[1] if len(sys.argv) > 1 else "1"
    host = sys.argv[2] if len(sys.argv) > 2 else "localhost"

    llamar_rest(id, host)
    llamar_grpc(id, host)