import time
from concurrent import futures
import threading
from flask import Flask, jsonify

import grpc
import service_pb2
import service_pb2_grpc

#LOGICA DE NEGOCIO Y DATOS COMPARTIDOS
DATABASE = {
    "1": {"id": "1", "name": "Laptop ThinkPad", "description": "16GB RAM, 512GB SSD"},
    "2": {"id": "2", "name": "Monitor Dell", "description": "27 pulgadas 4K"},
    "3": {"id": "3", "name": "Teclado Mecanico", "description": "Switches Cherry MX Red"}
}

def get_item_by_id(item_id: str):
    """Lógica central compartida entre REST y gRPC."""
    return DATABASE.get(item_id)


#CONTROLADOR REST
rest_app = Flask(__name__)

@rest_app.route("/items/<item_id>", methods=["GET"])
def rest_get_item(item_id):
    item = get_item_by_id(item_id)
    if not item:
        return jsonify({"error": "No hay datos para el ID proporcionado"}), 404
    return jsonify(item), 200

def start_rest():
    rest_app.run(host="0.0.0.0", port=8000, debug=False, use_reloader=False)


#CONTROLADOR gRPC
class ItemServiceServicer(service_pb2_grpc.ItemServiceServicer):
    def GetItem(self, request, context):
        item = get_item_by_id(request.id)
        if not item:
            context.abort(grpc.StatusCode.NOT_FOUND, "No hay datos para el ID proporcionado")
        return service_pb2.ItemResponse(
            id=item["id"],
            name=item["name"],
            description=item["description"]
        )

def start_grpc():
    grpc_server = grpc.server(futures.ThreadPoolExecutor(max_workers=5))
    service_pb2_grpc.add_ItemServiceServicer_to_server(ItemServiceServicer(), grpc_server)
    grpc_server.add_insecure_port("0.0.0.0:50051")
    grpc_server.start()
    grpc_server.wait_for_termination()


#inicio ambos controladores
if __name__ == "__main__":
    grpc_thread = threading.Thread(target=start_grpc, daemon=True)
    grpc_thread.start()
    print("[SERVER] Servidor gRPC escuchando en el puerto 50051...")

    print("[SERVER] Servidor REST escuchando en el puerto 8000...")
    start_rest()
