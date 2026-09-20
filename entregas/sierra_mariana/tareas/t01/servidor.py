import threading
from flask import Flask, jsonify
import grpc
from concurrent import futures
import servicio_pb2
import servicio_pb2_grpc
import logica

# --- Controlador REST ---
app = Flask(__name__)
@app.route('/info/<int:id>', methods=['GET'])
def get_info_rest(id):
    existe, datos = logica.obtener_datos(id)
    if existe: 
        return jsonify({"datos": datos}), 200
    return jsonify({"error": datos}), 404

def run_rest():
    app.run(host='0.0.0.0', port=8080)

# --- Controlador gRPC ---
class InfoServicer(servicio_pb2_grpc.ServicioInfoServicer):
    def ObtenerInfo(self, request, context):
        existe, datos = logica.obtener_datos(request.id)
        return servicio_pb2.InfoResponse(existe=existe, datos=datos)

def run_grpc():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=5))
    servicio_pb2_grpc.add_ServicioInfoServicer_to_server(InfoServicer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    server.wait_for_termination()

if __name__ == '__main__':
    threading.Thread(target=run_rest, daemon=True).start()
    run_grpc()