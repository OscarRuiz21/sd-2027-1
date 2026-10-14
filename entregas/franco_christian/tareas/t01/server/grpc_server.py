from concurrent import futures

import grpc

from proto import data_pb2
from proto import data_pb2_grpc

from logic import obtener_dato


class DataService(data_pb2_grpc.DataServiceServicer):

    def GetData(self, request, context):
        dato = obtener_dato(request.id)

        if dato is None:
            return data_pb2.GetDataResponse(
                found=False,
                id=request.id
            )

        return data_pb2.GetDataResponse(
            found=True,
            id=request.id,
            nombre=dato["nombre"],
            carrera=dato["carrera"]
        )


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

    data_pb2_grpc.add_DataServiceServicer_to_server(
        DataService(),
        server
    )

    server.add_insecure_port("[::]:50051")
    server.start()

    print("Servidor gRPC escuchando en el puerto 50051")

    server.wait_for_termination()


if __name__ == "__main__":
    serve()
