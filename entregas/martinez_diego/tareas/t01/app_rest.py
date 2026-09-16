from flask import Flask, jsonify, request

app = Flask(__name__)

# Base de datos simulada en memoria
productos = [
    {"id": "1", "nombre": "Laptop", "precio": 999.99}
]

@app.route('/productos/<string:prod_id>', methods=['GET'])
def obtener_producto(prod_id):
    producto = next((p for p in productos if p["id"] == prod_id), None)
    if producto:
        return jsonify(producto), 200
    return jsonify({"error": "Producto no encontrado"}), 404

@app.route('/productos', methods=['POST'])
def crear_producto():
    data = request.get_json()
    if not data or "nombre" not in data or "precio" not in data:
        return jsonify({"error": "Datos invalidos"}), 400
    
    nuevo = {
        "id": str(len(productos) + 1),
        "nombre": data.get("nombre"),
        "precio": float(data.get("precio"))
    }
    productos.append(nuevo)
    return jsonify(nuevo), 201

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)