# server_rest.py
# Controlador REST: solo traduce HTTP <-> logica.py.
# No repite la busqueda de datos, solo llama a get_item().

from flask import Flask, jsonify
from logica import get_item

app = Flask(__name__)


@app.route("/item/<item_id>")
def item(item_id):
    resultado = get_item(item_id)
    if not resultado["encontrado"]:
        return jsonify({"error": "no encontrado"}), 404
    return jsonify(resultado)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)