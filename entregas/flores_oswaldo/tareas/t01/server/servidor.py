import threading
from concurrent import futures

import grpc
from flask import Flask, jsonify

import logica
import service_pb2
import service_pb2_grpc


app = Flask(__name__)


@app.get("/items/<id_>")
def rest_buscar(id_):
    datos = logica.buscar(id_)
    if datos is None:
        return jsonify({"encontrado": False, "mensaje": "no hay datos"}), 404
    return jsonify({"encontrado": True, **datos})



class Consulta(service_pb2_grpc.ConsultaServicer):
    def Buscar(self, request, context):
        datos = logica.buscar(request.id)
        if datos is None:
            return service_pb2.BuscarResponse(encontrado=False)
        return service_pb2.BuscarResponse(
            encontrado=True,
            nombre=datos["nombre"],
            rol=datos["rol"],
            edad=datos["edad"],
        )


def servir_grpc():
    servidor = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    service_pb2_grpc.add_ConsultaServicer_to_server(Consulta(), servidor)
    servidor.add_insecure_port("[::]:50051")
    servidor.start()
    print("gRPC escuchando en el puerto 50051", flush=True)
    servidor.wait_for_termination()


if __name__ == "__main__":
    hilo = threading.Thread(target=servir_grpc, daemon=True)
    hilo.start()
    print("REST escuchando en el puerto 5000", flush=True)
    app.run(host="0.0.0.0", port=5000)