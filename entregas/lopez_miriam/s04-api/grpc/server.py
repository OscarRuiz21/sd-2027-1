from concurrent import futures
import grpc

import calculator_pb2
import calculator_pb2_grpc

from grpc_reflection.v1alpha import reflection


class CalculatorService(calculator_pb2_grpc.CalculatorServicer):

    def Sumar(self, request, context):
        resultado = request.a + request.b

        return calculator_pb2.SumaResponse(
            resultado=resultado
        )


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

    calculator_pb2_grpc.add_CalculatorServicer_to_server(
        CalculatorService(),
        server
    )

    service_names = (
        calculator_pb2.DESCRIPTOR.services_by_name["Calculator"].full_name,
        reflection.SERVICE_NAME,
    )

    reflection.enable_server_reflection(service_names, server)

    server.add_insecure_port("[::]:50051")
    server.start()

    print("Servidor gRPC ejecutándose en el puerto 50051")

    server.wait_for_termination()


if __name__ == "__main__":
    serve()