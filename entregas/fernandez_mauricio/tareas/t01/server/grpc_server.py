from concurrent import futures

import grpc

from proto import service_pb2
from proto import service_pb2_grpc

from service import obtener_usuario


class UsuarioService(service_pb2_grpc.UsuarioServiceServicer):

    def ObtenerUsuario(self, request, context):
        usuario = obtener_usuario(request.id)

        if usuario is None:
            context.set_code(grpc.StatusCode.NOT_FOUND)
            context.set_details("No hay datos para el ID solicitado")
            return service_pb2.UsuarioResponse()

        return service_pb2.UsuarioResponse(
            id=request.id,
            nombre=usuario["nombre"],
            edad=usuario["edad"]
        )


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

    service_pb2_grpc.add_UsuarioServiceServicer_to_server(
        UsuarioService(),
        server
    )

    server.add_insecure_port("[::]:50051")
    server.start()

    print("Servidor gRPC ejecutándose en el puerto 50051")

    server.wait_for_termination()


if __name__ == "__main__":
    serve()