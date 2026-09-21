# servidor/server_rest.py

from flask import Flask, jsonify
import db #

app = Flask(__name__)

#Se define el recurso (/data) y el verbo (GET)
@app.route('/data/<requested_id>', methods=['GET'])
def handle_get_data(requested_id):

    # El controlador llama al metodo de db
    result = db.get_data_by_id(requested_id)

    if result["success"]:
        #Se entregan datos en formato JSON
        response_data = {
            "id": result["id"],
            "name": result["name"],
            "error": result["error"]
        }
        return jsonify(response_data), 200

    else:
        # Error del cliente
        error_data = {
            "id": "",
            "name": "",
            "error": result["error"]
        }
        return jsonify(error_data), 404

if __name__ == '__main__':
    print("Iniciando servidor REST en el puerto 8080...")
    # Escucha en todos los segmentos de red (0.0.0.0)
    app.run(host='0.0.0.0', port=8080)
