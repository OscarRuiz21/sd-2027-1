from concurrent.futures import ThreadPoolExecutor

import grpc
from flask import Flask, request

import catalogo_pb2
import catalogo_pb2_grpc
from catalogo import buscar_libro

app = Flask(__name__)


@app.get("/libros")
def consultar_rest():
    identificador = request.args.get("id")
    if not identificador:
        return {"error": "Falta el ID"}, 400

    libro = buscar_libro(identificador)
    if libro is None:
        return {"error": "Libro no encontrado"}, 404

    return libro


class Catalogo(catalogo_pb2_grpc.CatalogoServicer):
    def Consultar(self, solicitud, contexto):
        if not solicitud.id:
            contexto.abort(grpc.StatusCode.INVALID_ARGUMENT, "Falta el ID")

        libro = buscar_libro(solicitud.id)
        if libro is None:
            contexto.abort(grpc.StatusCode.NOT_FOUND, "Libro no encontrado")

        return catalogo_pb2.Libro(**libro)


if __name__ == "__main__":
    servidor_grpc = grpc.server(ThreadPoolExecutor(max_workers=4))
    catalogo_pb2_grpc.add_CatalogoServicer_to_server(Catalogo(), servidor_grpc)
    servidor_grpc.add_insecure_port("[::]:50051")
    servidor_grpc.start()
    app.run(host="0.0.0.0", port=8080)
