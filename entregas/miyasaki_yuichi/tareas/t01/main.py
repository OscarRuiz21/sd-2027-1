import os
import sys

import uvicorn

import controlador_grpc
import nucleo
from controlador_rest import app

PUERTO_REST = int(os.getenv("PUERTO_REST", "8000"))
PUERTO_GRPC = int(os.getenv("PUERTO_GRPC", "50051"))


def main():
    servidor_grpc = controlador_grpc.construir_servidor(PUERTO_GRPC)
    servidor_grpc.start()

    print(f"[servidor] catalogo en memoria: {nucleo.listar_ids()}", flush=True)
    print(f"[servidor] gRPC  escuchando en 0.0.0.0:{PUERTO_GRPC}", flush=True)
    print(f"[servidor] REST  escuchando en 0.0.0.0:{PUERTO_REST}", flush=True)
    print("[servidor] ambas interfaces comparten nucleo.consultar_articulo()", flush=True)

    try:
        uvicorn.run(app, host="0.0.0.0", port=PUERTO_REST, log_level="info")
    except KeyboardInterrupt:
        pass
    finally:
        servidor_grpc.stop(grace=2).wait()
        print("[servidor] detenido", flush=True)


if __name__ == "__main__":
    sys.exit(main())
