import threading
import uvicorn

from server.grpc_server import iniciar_servidor


def iniciar_grpc():
    iniciar_servidor()


if __name__ == "__main__":

    hilo_grpc = threading.Thread(
        target=iniciar_grpc,
        daemon=True
    )

    hilo_grpc.start()

    print("Servidor REST iniciado en el puerto 8000")

    uvicorn.run(
        "server.rest:app",
        host="0.0.0.0",
        port=8000
    )