import os

import grpc

import catalogo_pb2
import catalogo_pb2_grpc

DESTINO = f"{os.getenv('SERVIDOR_HOST', 'servidor')}:{os.getenv('PUERTO_GRPC', '50051')}"

canal = grpc.insecure_channel(DESTINO)
stub = catalogo_pb2_grpc.CatalogoStub(canal)

print(f"### Pruebas manuales por gRPC contra {DESTINO} ###")
print()

print("$ Consultar(id='A-100')")
print(stub.Consultar(catalogo_pb2.PeticionArticulo(id="A-100")))

print("$ Consultar(id='a-100 ')")
print(stub.Consultar(catalogo_pb2.PeticionArticulo(id="a-100 ")))

print("$ Consultar(id='z-999')")
try:
    stub.Consultar(catalogo_pb2.PeticionArticulo(id="z-999"))
except grpc.RpcError as e:
    print("code   :", e.code())
    print("details:", e.details())
print()

print("$ Listar()")
print(list(stub.Listar(catalogo_pb2.PeticionVacia()).ids))

canal.close()
