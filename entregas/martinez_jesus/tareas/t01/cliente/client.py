# cliente/client.py
import requests
import grpc
import os

# Stubs generados por protoc
import data_pb2
import data_pb2_grpc

HOST_REST = os.getenv('HOST_REST', 'http://localhost:8080')
HOST_GRPC = os.getenv('HOST_GRPC', 'localhost:50051')

def test_rest(id_searched):
    print(f"\n--- Probando REST (ID: {id_searched}) ---")
    url = f"{HOST_REST}/data/{id_searched}"
    
    try:
        # Petición GET a la API REST
        response = requests.get(url)
        
        print(f"Código de Estado: {response.status_code}")
        # REST devuelve JSON, requests lo convierte a datos de Python
        print(f"Datos de JSON: {response.json()}")
    except Exception as e:
        print(f"Error en REST: {e}")

def test_grpc(id_searched):
    print(f"\n--- Probando gRPC (ID: {id_searched}) ---")
    
    # Canal de comunicación (TCP/HTTP2)
    with grpc.insecure_channel(HOST_GRPC) as channel:
        # Creación del cliente (Stub) en el canal
        stub = data_pb2_grpc.DataServiceStub(channel)
        
        # Petición al servicio gRPC a traves de Protobuf
        request = data_pb2.DataRequest(id=id_searched)
        
        try:
            # RPC como función local
            response = stub.GetData(request)

            # Datos del Protobuf devueltos por el servidor
            print("Respuesta Protobuf recibida:")
            print(f"ID: '{response.id}'")
            print(f"Name: '{response.name}'")
            print(f"Error: '{response.error}'")
        except grpc.RpcError as e:
            print(f"Error en gRPC: {e}")

if __name__ == '__main__':
    print("Iniciando pruebas del cliente...")
    
    # Prueba 1
    test_rest("1")
    test_grpc("1")
    
    # Prueba 2
    test_rest("99")
    test_grpc("99")
    
    print("\nPruebas finalizadas.")
