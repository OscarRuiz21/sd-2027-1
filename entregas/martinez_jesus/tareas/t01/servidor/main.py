# servidor/main.py

import threading
import server_grpc
from server_rest import app

def run_grpc():
    # Inicia gRPC y espera conexiones
    grpc_server = server_grpc.serve()
    grpc_server.wait_for_termination()

def run_rest():
    # Inicia Flask en el puerto 8080
    # use_reloader=False evita que Flask duplique hilos en background
    app.run(host='0.0.0.0', port=8080, use_reloader=False)

if __name__ == '__main__':
    print("*** Iniciando servicios (REST + gRPC) ***")

    # Hilo secundario para gRCP
    grpc_thread = threading.Thread(target=run_grpc, daemon=True)
    grpc_thread.start()

    # Hilo principal para el servidor REST
    run_rest()
