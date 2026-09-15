import grpc
import requests

import service_pb2
import service_pb2_grpc


def consultar_rest(id_usuario):
    respuesta = requests.get(
        f"http://server:5000/usuarios/{id_usuario}"
    )

    if respuesta.status_code == 200:
        return respuesta.json()

    return {
        "error": respuesta.json()["error"],
        "status": respuesta.status_code
    }

def consultar_grpc(id_usuario):
    with grpc.insecure_channel("server:50051") as channel:
        stub = service_pb2_grpc.UsuarioServiceStub(channel)

        try:
            respuesta = stub.ObtenerUsuario(
                service_pb2.UsuarioRequest(id=id_usuario)
            )

            return {
                "id": respuesta.id,
                "nombre": respuesta.nombre,
                "edad": respuesta.edad
            }

        except grpc.RpcError as error:
            return {
                "error": error.details(),
                "status": error.code().name
            }

print("Respuesta REST:")
print(consultar_rest(1))

print("\nRespuesta gRPC:")
print(consultar_grpc(1))