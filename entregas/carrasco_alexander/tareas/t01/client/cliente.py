import requests
import grpc
import sys
import os


# Permitimos importar los archivos generados de gRPC
ruta_t01 = os.path.abspath(
    os.path.join(
        os.path.dirname(__file__),
        ".."
    )
)

ruta_generated = os.path.join(
    ruta_t01,
    "server",
    "generated"
)

sys.path.insert(0, ruta_t01)
sys.path.insert(0, ruta_generated)


from server.generated import servicio_pb2
from server.generated import servicio_pb2_grpc


def consultar_rest(id):
    servidor = os.getenv("SERVIDOR", "localhost")

    respuesta = requests.get(
        f"http://{servidor}:8000/personas/{id}"
    )

    return respuesta.json()


def consultar_grpc(id):
    servidor = os.getenv("SERVIDOR", "localhost")

    canal = grpc.insecure_channel(
        f"{servidor}:50051"
    )

    cliente = servicio_pb2_grpc.PersonaServiceStub(canal)

    solicitud = servicio_pb2.PersonaRequest(id=id)

    respuesta = cliente.ObtenerPersona(solicitud)

    canal.close()

    return respuesta


if __name__ == "__main__":

    id = 1

    print("Consulta mediante REST:")
    print(consultar_rest(id))

    print("\nConsulta mediante gRPC:")
    respuesta_grpc = consultar_grpc(id)

if respuesta_grpc.mensaje:
    print(respuesta_grpc.mensaje)
else:
    print(
        f"id: {respuesta_grpc.id}, "
        f"nombre: {respuesta_grpc.nombre}"
    )