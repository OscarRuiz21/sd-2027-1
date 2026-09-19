from concurrent import futures
from threading import Thread

import grpc
from flask import Flask, jsonify
from grpc_reflection.v1alpha import reflection

import service_pb2
import service_pb2_grpc

from logic import buscar_usuario


# -------------------------
# REST
# -------------------------

app = Flask(__name__)


@app.route("/usuarios/<id>", methods=["GET"])
def obtener_usuario_rest(id):
    usuario = buscar_usuario(id)

    if usuario is None:
        return jsonify({
            "error": "Usuario no encontrado"
        }), 404

    return jsonify({
        "id": id,
        "nombre": usuario["nombre"],
        "correo": usuario["correo"]
    }), 200


def iniciar_rest():
    app.run(
        host="0.0.0.0",
        port=5000,
        use_reloader=False
    )


# -------------------------
# gRPC
# -------------------------

class UsuariosService(service_pb2_grpc.UsuariosServicer):

    def BuscarUsuario(self, request, context):
        usuario = buscar_usuario(request.id)

        if usuario is None:
            context.abort(
                grpc.StatusCode.NOT_FOUND,
                "Usuario no encontrado"
            )

        return service_pb2.UsuarioResponse(
            id=request.id,
            nombre=usuario["nombre"],
            correo=usuario["correo"]
        )


def iniciar_grpc():
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10)
    )

    service_pb2_grpc.add_UsuariosServicer_to_server(
        UsuariosService(),
        server
    )

    service_names = (
        service_pb2.DESCRIPTOR.services_by_name["Usuarios"].full_name,
        reflection.SERVICE_NAME,
    )

    reflection.enable_server_reflection(
        service_names,
        server
    )

    server.add_insecure_port("[::]:50051")
    server.start()

    print("Servidor gRPC ejecutandose en el puerto 50051")

    server.wait_for_termination()


# -------------------------
# INICIO
# -------------------------

if __name__ == "__main__":
    rest_thread = Thread(
        target=iniciar_rest,
        daemon=True
    )

    rest_thread.start()

    print("Servidor REST ejecutandose en el puerto 5000")

    iniciar_grpc()