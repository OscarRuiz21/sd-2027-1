# iniciar_servidores.py
# Arranca el servidor REST y el servidor gRPC al mismo tiempo,
# dentro del mismo contenedor, cada uno en su propio hilo.

import threading
import server_rest
import server_grpc

if __name__ == "__main__":
    hilo_grpc = threading.Thread(target=server_grpc.serve)
    hilo_grpc.start()

    server_rest.app.run(host="0.0.0.0", port=5000)