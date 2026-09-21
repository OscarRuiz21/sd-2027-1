"""Inicia REST y gRPC dentro del mismo programa servidor."""

from threading import Thread

from grpc_server import iniciar_grpc
from rest_server import iniciar_rest


if __name__ == "__main__":
    hilo_rest = Thread(target=iniciar_rest, daemon=True)
    hilo_rest.start()
    print("REST disponible en el puerto 8081", flush=True)

    # gRPC queda en el hilo principal y mantiene vivo el programa.
    iniciar_grpc()
