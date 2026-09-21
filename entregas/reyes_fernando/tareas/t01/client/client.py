import os
import sys

import grpc
import requests

import servicio_pb2
import servicio_pb2_grpc


REST_URL = os.getenv("REST_URL", "http://localhost:5001")
GRPC_HOST = os.getenv("GRPC_HOST", "localhost:50051")


def consultar_rest(usuario_id):
    url = f"{REST_URL}/usuarios/{usuario_id}"

    try:
        respuesta = requests.get(url, timeout=5)

        if respuesta.status_code == 404:
            print("REST: Usuario no encontrado")
            return

        respuesta.raise_for_status()
        usuario = respuesta.json()

        print("Respuesta REST:")
        print(f"ID: {usuario['id']}")
        print(f"Nombre: {usuario['nombre']}")
        print(f"Materia: {usuario['materia']}")
        print(f"Correo: {usuario['correo']}")
        print(f"Celular: {usuario['celular']}")

    except requests.RequestException as error:
        print(f"Error al comunicarse por REST: {error}")


def consultar_grpc(usuario_id):
    try:
        with grpc.insecure_channel(GRPC_HOST) as canal:
            cliente = servicio_pb2_grpc.UsuarioServiceStub(canal)

            respuesta = cliente.ObtenerUsuario(
                servicio_pb2.UsuarioRequest(id=usuario_id)
            )

            print("Respuesta gRPC:")
            print(f"ID: {respuesta.id}")
            print(f"Nombre: {respuesta.nombre}")
            print(f"Materia: {respuesta.materia}")
            print(f"Correo: {respuesta.correo}")
            print(f"Celular: {respuesta.celular}")

    except grpc.RpcError as error:
        if error.code() == grpc.StatusCode.NOT_FOUND:
            print("gRPC: Usuario no encontrado")
        else:
            print(f"Error al comunicarse por gRPC: {error.details()}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Uso:")
        print("python3 client/client.py rest <id>")
        print("python3 client/client.py grpc <id>")
        sys.exit(1)

    protocolo = sys.argv[1].lower()

    try:
        usuario_id = int(sys.argv[2])
    except ValueError:
        print("El ID debe ser un numero entero")
        sys.exit(1)

    if protocolo == "rest":
        consultar_rest(usuario_id)
    elif protocolo == "grpc":
        consultar_grpc(usuario_id)
    else:
        print("Protocolo no valido. Usa 'rest' o 'grpc'.")
