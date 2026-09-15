import threading

from app import app
from grpc_server import serve


def iniciar_rest():
    app.run(host="0.0.0.0", port=5000)


if __name__ == "__main__":
    hilo_grpc = threading.Thread(target=serve, daemon=True)
    hilo_grpc.start()

    iniciar_rest()