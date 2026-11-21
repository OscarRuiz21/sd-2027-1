from flask import Flask, jsonify
from logic import obtener_dato

app = Flask(__name__)


@app.get("/data/<int:id>")
def obtener_dato_rest(id):
    dato = obtener_dato(id)

    if dato is None:
        return jsonify({
            "error": "No hay datos para el ID solicitado"
        }), 404

    return jsonify({
        "id": id,
        **dato
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
