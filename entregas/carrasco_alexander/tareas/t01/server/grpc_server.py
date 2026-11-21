import sys
import os

# Agregamos la carpeta generated a las rutas donde Python busca módulos
ruta_generated = os.path.join(
    os.path.dirname(__file__),
    "generated"
)

sys.path.insert(0, ruta_generated)


import grpc
from concurrent import futures

from server.service import buscar_por_id
from server.generated import servicio_pb2
from server.generated import servicio_pb2_grpc


class PersonaService(servicio_pb2_grpc.PersonaServiceServicer):

    def ObtenerPersona(self, request, context):
        persona = buscar_por_id(request.id)

        if persona is None:
            return servicio_pb2.PersonaResponse(
                mensaje="No hay datos"
            )

        return servicio_pb2.PersonaResponse(
            id=persona["id"],
            nombre=persona["nombre"]
        )


def iniciar_servidor():
    servidor = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10)
    )

    servicio_pb2_grpc.add_PersonaServiceServicer_to_server(
        PersonaService(),
        servidor
    )

    servidor.add_insecure_port("[::]:50051")

    servidor.start()

    print("Servidor gRPC iniciado en el puerto 50051")

    servidor.wait_for_termination()


if __name__ == "__main__":
    iniciar_servidor()