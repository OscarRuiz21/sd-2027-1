import requests
import grpc
import service_pb2
import service_pb2_grpc
import time

time.sleep(3)

print("--- PRUEBAS REST ---")
try:
    for i in ("1", "99"):
        r = requests.get(f'http://server:5000/api/{i}')
        print(f"ID {i}: HTTP {r.status_code} ->", r.json())
except Exception as e:
    print("Error REST:", e)

print("\n--- PRUEBAS gRPC ---")
try:
    channel = grpc.insecure_channel('server:50051')
    stub = service_pb2_grpc.BusquedaStub(channel)
    for i in ("1", "99"):
        resp = stub.Buscar(service_pb2.Peticion(id=i))
        print(f"ID {i}: existe={resp.existe} info={resp.info!r}")
except Exception as e:
    print("Error gRPC:", e)
