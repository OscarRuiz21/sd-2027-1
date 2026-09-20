from concurrent import futures
import grpc

import servicio_pb2
import servicio_pb2_grpc
from logica import buscar_por_id


class ServicioItemsImpl(servicio_pb2_grpc.ServicioItemsServicer):
    def Buscar(self, request, context):
        resultado = buscar_por_id(request.id)
        mensaje = servicio_pb2.RespuestaItem(**resultado)
        print("Bytes gRPC:", len(mensaje.SerializeToString()))
        return mensaje


def main():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    servicio_pb2_grpc.add_ServicioItemsServicer_to_server(ServicioItemsImpl(), server)
    server.add_insecure_port("0.0.0.0:50051")
    server.start()
    print("gRPC escuchando en :50051")
    server.wait_for_termination()


if __name__ == "__main__":
    main()