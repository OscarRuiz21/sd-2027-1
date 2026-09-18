import threading
from concurrent import futures
from flask import Flask, jsonify
import grpc
import service_pb2
import service_pb2_grpc
from logic import consultar_datos

app = Flask(__name__)

@app.route('/api/<id>', methods=['GET'])
def rest_buscar(id):
    existe, info = consultar_datos(id)
    return jsonify({"existe": existe, "info": info}), (200 if existe else 404)

class BusquedaServicer(service_pb2_grpc.BusquedaServicer):
    def Buscar(self, request, context):
        existe, info = consultar_datos(request.id)
        return service_pb2.Respuesta(existe=existe, info=info)

def serve_grpc():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    service_pb2_grpc.add_BusquedaServicer_to_server(BusquedaServicer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    server.wait_for_termination()

if __name__ == '__main__':
    threading.Thread(target=serve_grpc, daemon=True).start()
    app.run(host='0.0.0.0', port=5000)
