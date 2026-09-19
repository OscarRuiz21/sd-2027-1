from concurrent import futures

import grpc

from app.logic import get_student
from app.generated import service_pb2
from app.generated import service_pb2_grpc


class StudentService(service_pb2_grpc.StudentServiceServicer):

    def GetStudent(self, request, context):

        student = get_student(request.id)

        if student is None:
            return service_pb2.StudentResponse(
                found=False
            )

        return service_pb2.StudentResponse(
            id=student["id"],
            name=student["name"],
            age=student["age"],
            found=True
        )


def serve():

    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10)
    )

    service_pb2_grpc.add_StudentServiceServicer_to_server(
        StudentService(),
        server
    )

    server.add_insecure_port("[::]:50051")

    server.start()

    print("Servidor gRPC ejecutándose en el puerto 50051")

    server.wait_for_termination()


if __name__ == "__main__":
    serve()