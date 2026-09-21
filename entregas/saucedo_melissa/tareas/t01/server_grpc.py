# server_grpc.py
# Controlador gRPC: solo traduce gRPC <-> logica.py.
# No repite la busqueda de datos, solo llama a get_item(), igual que REST.

from concurrent import futures
import grpc

import servicio_pb2
import servicio_pb2_grpc
from logica import get_item


class ServicioItemsServicer(servicio_pb2_grpc.ServicioItemsServicer):
    def ObtenerItem(self, request, context):
        resultado = get_item(request.id)
        if not resultado["encontrado"]:
            return servicio_pb2.ItemResponse(encontrado=False)
        return servicio_pb2.ItemResponse(
            encontrado=True,
            nombre=resultado["nombre"],
            carrera=resultado["carrera"],
        )


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    servicio_pb2_grpc.add_ServicioItemsServicer_to_server(
        ServicioItemsServicer(), server
    )
    server.add_insecure_port("[::]:50051")
    server.start()
    print("Servidor gRPC escuchando en el puerto 50051")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()