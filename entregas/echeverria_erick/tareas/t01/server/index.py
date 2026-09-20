import threading
from rest import iniciar_rest
from grpc_server import iniciar_grpc

if __name__ == "__main__":
    hilo_grpc = threading.Thread(target=iniciar_grpc, args=(50051,))
    hilo_grpc.start()

    iniciar_rest(3000)