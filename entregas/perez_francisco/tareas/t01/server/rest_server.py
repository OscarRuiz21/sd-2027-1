
from flask import Flask, jsonify

from service import buscar_producto

app = Flask(__name__)


@app.get("/health")
def health():
    return jsonify({"estado": "ok"}), 200


@app.get("/products/<int:producto_id>")
def obtener_producto(producto_id):
    producto = buscar_producto(producto_id)

    if producto is None:
        return jsonify(
            {
                "id": producto_id,
                "encontrado": False,
                "mensaje": "No hay datos para ese ID",
            }
        ), 404

    return jsonify(
        {
            **producto,
            "encontrado": True,
            "mensaje": "Producto encontrado",
        }
    ), 200


def iniciar_rest():
    app.run(host="0.0.0.0", port=8080, debug=False, use_reloader=False)
