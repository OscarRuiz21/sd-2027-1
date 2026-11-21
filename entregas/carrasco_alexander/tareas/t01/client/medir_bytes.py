import sys
import os
import requests
import grpc


# Ruta principal del proyecto t01
ruta_t01 = os.path.abspath(
    os.path.join(
        os.path.dirname(__file__),
        ".."
    )
)

# Ruta donde están los archivos generados por gRPC
ruta_generated = os.path.join(
    ruta_t01,
    "server",
    "generated"
)

# Agregamos las rutas para encontrar los módulos
sys.path.insert(0, ruta_t01)
sys.path.insert(0, ruta_generated)


from server.generated import servicio_pb2


def medir_rest(id):
    servidor = os.getenv("SERVIDOR", "localhost")

    respuesta = requests.get(
        f"http://{servidor}:8000/personas/{id}"
    )

    bytes_respuesta = len(respuesta.content)

    print("REST")
    print("Respuesta:", respuesta.text)
    print("Bytes de respuesta:", bytes_respuesta)

    return bytes_respuesta


def medir_grpc(id):
    servidor = os.getenv("SERVIDOR", "localhost")

    canal = grpc.insecure_channel(
        f"{servidor}:50051"
    )

    solicitud = servicio_pb2.PersonaRequest(id=id)

    respuesta = canal.unary_unary(
        "/personas.PersonaService/ObtenerPersona",
        request_serializer=servicio_pb2.PersonaRequest.SerializeToString,
        response_deserializer=servicio_pb2.PersonaResponse.FromString
    )(solicitud)

    bytes_solicitud = len(solicitud.SerializeToString())
    bytes_respuesta = len(respuesta.SerializeToString())

    print("\ngRPC")
    print("Respuesta:", respuesta)
    print("Bytes de solicitud:", bytes_solicitud)
    print("Bytes de respuesta:", bytes_respuesta)
    print("Bytes totales de mensajes:", bytes_solicitud + bytes_respuesta)

    canal.close()


if __name__ == "__main__":

    id = 1

    medir_rest(id)
    medir_grpc(id)