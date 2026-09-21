# servidor/server_grpc.py

import grpc
from concurrent import futures
import db 

# Importamos los módulos generados por protoc
import data_pb2
import data_pb2_grpc

# Herenica de la clase base generada a partir del contrato .proto
class DataService(data_pb2_grpc.DataServiceServicer):

    # Implementamos el RPC definido en el .proto
    def GetData(self, request, context):
        # request.id es el valor deserializado desde Protobuf
        requested_id = request.id

        #Se invoca el metodo de db
        result = db.get_data_by_id(requested_id)
        print(f"Datos obtenidos de la base de datos: {result}")

        # Se construye y devuelve el mensaje Protobuf
        return data_pb2.DataResponse(
            id=result["id"],
            name=result["name"],
            error=result["error"]
        )

def serve():
    # Creamos el servidor gRPC con un pool de hilos peticiones concurrentes
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

    # Registramos DataService en el servidor gRPC
    data_pb2_grpc.add_DataServiceServicer_to_server(DataService(), server)

    # Enlazamos el socket TCP en 0.0.0.0:50051 sin TLS(insecure)
    server.add_insecure_port('0.0.0.0:50051')

    print("Iniciando servidor gRPC en el puerto 50051...")
    server.start()
    return server

if __name__ == '__main__':
    server = serve()
    server.wait_for_termination()
