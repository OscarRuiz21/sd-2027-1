# Mide cuantos bytes viajan en la misma llamada por REST y por gRPC.
# Se corre dentro del contenedor del cliente: docker compose run --rm client python medir_bytes.py
import requests
import grpc
import service_pb2
import service_pb2_grpc

print("=== REST (HTTP/1.1 + JSON) ===")
for i in ("1", "99"):
    r = requests.get(f"http://server:5000/api/{i}")
    cuerpo = len(r.content)
    # la linea de estado y los encabezados que mando Flask, tal cual llegaron
    encabezados = len(f"HTTP/1.1 {r.status_code} {r.reason}\r\n") + sum(len(f"{k}: {v}\r\n") for k, v in r.headers.items()) + 2
    pet = r.request
    peticion = len(f"{pet.method} {pet.path_url} HTTP/1.1\r\n") + sum(len(f"{k}: {v}\r\n") for k, v in pet.headers.items()) + len("Host: server:5000\r\n") + 2
    print(f"ID {i}: cuerpo JSON = {cuerpo} B | encabezados respuesta = {encabezados} B | peticion completa = {peticion} B | total = {cuerpo + encabezados + peticion} B")

print("=== gRPC (HTTP/2 + Protobuf) ===")
stub = service_pb2_grpc.BusquedaStub(grpc.insecure_channel("server:50051"))
for i in ("1", "99"):
    pet = service_pb2.Peticion(id=i)
    resp = stub.Buscar(pet)
    print(f"ID {i}: Peticion.ByteSize() = {pet.ByteSize()} B | Respuesta.ByteSize() = {resp.ByteSize()} B | con el prefijo de 5 B de gRPC = {pet.ByteSize() + 5} + {resp.ByteSize() + 5} B")
