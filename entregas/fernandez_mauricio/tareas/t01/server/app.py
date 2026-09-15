from flask import Flask, jsonify
from service import obtener_usuario

app = Flask(__name__)


@app.get("/usuarios/<int:id_usuario>")
def obtener_usuario_rest(id_usuario):
    usuario = obtener_usuario(id_usuario)

    if usuario is None:
        return jsonify({
            "error": "No hay datos para el ID solicitado"
        }), 404

    return jsonify({
        "id": id_usuario,
        **usuario
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)