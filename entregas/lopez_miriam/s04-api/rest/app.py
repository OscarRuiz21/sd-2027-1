from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route("/sumar", methods=["POST"])
def sumar():
    datos = request.get_json()

    if not datos or "a" not in datos or "b" not in datos:
        return jsonify({
            "error": "Se deben enviar los valores a y b"
        }), 400

    try:
        a = float(datos["a"])
        b = float(datos["b"])
    except (ValueError, TypeError):
        return jsonify({
            "error": "Los valores a y b deben ser numeros"
        }), 400

    resultado = a + b

    return jsonify({
        "resultado": resultado
    }), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)