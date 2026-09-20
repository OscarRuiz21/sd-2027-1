from flask import Flask, jsonify
from logica import buscar_por_id

def iniciar_rest(puerto):
    app = Flask(__name__)

    @app.route("/item/<id>")
    def obtener_item(id):
        resultado = buscar_por_id(id)
        return jsonify(resultado)

    app.run(host="0.0.0.0", port=puerto)