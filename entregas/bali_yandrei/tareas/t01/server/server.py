from concurrent import futures
from threading import Thread

import grpc
from flask import Flask, jsonify

import servicio_pb2
import servicio_pb2_grpc
from servicio import buscar_usuario


app = Flask(__name__)


# REST
@app.route("/usuario/<int:id_usuario>")
def obtener_usuario_rest(id_usuario):
    usuario = buscar_usuario(id_usuario)

    if usuario is None:
        return jsonify({"mensaje": "No hay datos"}), 404

    return jsonify(usuario)


# gRPC
class UsuarioService(servicio_pb2_grpc.UsuarioServiceServicer):

    def BuscarUsuario(self, request, context):
        usuario = buscar_usuario(request.id)

        if usuario is None:
            return servicio_pb2.UsuarioResponse(
                encontrado=False
            )

        return servicio_pb2.UsuarioResponse(
            id=usuario["id"],
            nombre=usuario["nombre"],
            carrera=usuario["carrera"],
            encontrado=True
        )


def iniciar_rest():
    app.run(host="0.0.0.0", port=5000)


def iniciar_grpc():
    servidor = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

    servicio_pb2_grpc.add_UsuarioServiceServicer_to_server(
        UsuarioService(),
        servidor
    )

    servidor.add_insecure_port("[::]:50051")
    servidor.start()

    print("Servidor gRPC iniciado en puerto 50051")

    servidor.wait_for_termination()


if __name__ == "__main__":
    Thread(target=iniciar_rest, daemon=True).start()
    iniciar_grpc()