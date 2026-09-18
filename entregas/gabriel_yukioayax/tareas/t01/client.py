import requests
import grpc
import service_pb2
import service_pb2_grpc
import time

time.sleep(3)

print("--- PRUEBAS REST ---")
try:
    print("ID 1:", requests.get('http://server:5000/api/1').json())
    print("ID 99:", requests.get('http://server:5000/api/99').json())
except Exception as e:
    print("Error REST:", e)

print("\n--- PRUEBAS gRPC ---")
try:
    channel = grpc.insecure_channel('server:50051')
    stub = service_pb2_grpc.BusquedaStub(channel)
    print("ID 1:", stub.Buscar(service_pb2.Peticion(id="1")))
    print("ID 99:", stub.Buscar(service_pb2.Peticion(id="99")))
except Exception as e:
    print("Error gRPC:", e)
