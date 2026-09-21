from concurrent import futures

import grpc
from flask import Flask, jsonify

import servicio_pb2
import servicio_pb2_grpc
from logic import buscar_usuario


app = Flask(__name__)


# -------------------------
# Controlador REST
# -------------------------

@app.route("/usuarios/<int:usuario_id>", methods=["GET"])
def obtener_usuario_rest(usuario_id):
    usuario = buscar_usuario(usuario_id)

    if usuario is None:
        return jsonify({
            "error": "Usuario no encontrado"
        }), 404

    return jsonify(usuario), 200


# -------------------------
# Controlador gRPC
# -------------------------

class UsuarioService(servicio_pb2_grpc.UsuarioServiceServicer):

    def ObtenerUsuario(self, request, context):
        usuario = buscar_usuario(request.id)

        if usuario is None:
            context.abort(
                grpc.StatusCode.NOT_FOUND,
                "Usuario no encontrado"
            )

        return servicio_pb2.UsuarioResponse(
            id=usuario["id"],
            nombre=usuario["nombre"],
            materia=usuario["materia"],
            correo=usuario["correo"],
            celular=usuario["celular"]
        )


def iniciar_grpc():
    servidor_grpc = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10)
    )

    servicio_pb2_grpc.add_UsuarioServiceServicer_to_server(
        UsuarioService(),
        servidor_grpc
    )

    servidor_grpc.add_insecure_port("[::]:50051")
    servidor_grpc.start()

    print("Servidor gRPC ejecutandose en el puerto 50051")

    return servidor_grpc


# -------------------------
# Inicio del servidor
# -------------------------

if __name__ == "__main__":
    servidor_grpc = iniciar_grpc()

    try:
        app.run(host="0.0.0.0", port=5001)
    finally:
        servidor_grpc.stop(0)
