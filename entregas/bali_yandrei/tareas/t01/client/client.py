
import os
import requests
import grpc

import servicio_pb2
import servicio_pb2_grpc
SERVER_HOST = os.getenv("SERVER_HOST", "localhost")

id_usuario = 1


# REST
respuesta = requests.get(
    f"http://{SERVER_HOST}:5000/usuario/{id_usuario}"
)

print("Respuesta REST:")
print(respuesta.json())


# gRPC
canal = grpc.insecure_channel(f"{SERVER_HOST}:50051")
cliente = servicio_pb2_grpc.UsuarioServiceStub(canal)

respuesta_grpc = cliente.BuscarUsuario(
    servicio_pb2.UsuarioRequest(id=id_usuario)
)

print("\nRespuesta gRPC:")

if respuesta_grpc.encontrado:
    print({
        "id": respuesta_grpc.id,
        "nombre": respuesta_grpc.nombre,
        "carrera": respuesta_grpc.carrera
    })
else:
    print("No hay datos")