import os
import sys
import requests
import grpc

import service_pb2
import service_pb2_grpc


REST_URL = os.getenv("REST_URL", "http://localhost:5000")
GRPC_HOST = os.getenv("GRPC_HOST", "localhost:50051")


def buscar_rest(id):
    respuesta = requests.get(f"{REST_URL}/usuarios/{id}")

    if respuesta.status_code == 200:
        usuario = respuesta.json()

        print("Resultado REST:")
        print(f"ID: {usuario['id']}")
        print(f"Nombre: {usuario['nombre']}")
        print(f"Correo: {usuario['correo']}")
    else:
        print("Usuario no encontrado")


def buscar_grpc(id):
    canal = grpc.insecure_channel(GRPC_HOST)
    cliente = service_pb2_grpc.UsuariosStub(canal)

    try:
        respuesta = cliente.BuscarUsuario(
            service_pb2.UsuarioRequest(id=id)
        )

        print("Resultado gRPC:")
        print(f"ID: {respuesta.id}")
        print(f"Nombre: {respuesta.nombre}")
        print(f"Correo: {respuesta.correo}")

    except grpc.RpcError as error:
        if error.code() == grpc.StatusCode.NOT_FOUND:
            print("Usuario no encontrado")
        else:
            print("Error:", error.details())


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Uso:")
        print("py client.py rest ID")
        print("py client.py grpc ID")
        sys.exit(1)

    tipo = sys.argv[1].lower()
    id = sys.argv[2]

    if tipo == "rest":
        buscar_rest(id)

    elif tipo == "grpc":
        buscar_grpc(id)

    else:
        print("Debes elegir rest o grpc")