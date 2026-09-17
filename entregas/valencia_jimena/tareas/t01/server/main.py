import threading
from grpc_server import serve as serve_grpc
from rest_server import app

if __name__ == "__main__":
    grpc_thread = threading.Thread(target=serve_grpc, daemon=True)
    grpc_thread.start()

    app.run(host="0.0.0.0", port=8080)