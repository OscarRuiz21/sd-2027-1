# Hace la llamada con ID 1 por el protocolo que se le pida (n veces), para medirla con tcpdump.
# uso: python una_llamada.py rest|grpc [n]
import sys, time, requests, grpc, service_pb2, service_pb2_grpc
n = int(sys.argv[2]) if len(sys.argv) > 2 else 1
if sys.argv[1] == "rest":
    for _ in range(n):
        print(requests.get("http://server:5000/api/1").json()); time.sleep(0.5)
else:
    stub = service_pb2_grpc.BusquedaStub(grpc.insecure_channel("server:50051"))
    for _ in range(n):
        print(stub.Buscar(service_pb2.Peticion(id="1")), end=""); time.sleep(0.5)
