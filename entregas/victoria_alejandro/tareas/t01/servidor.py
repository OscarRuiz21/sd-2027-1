import threading
from concurrent import futures
import grpc
from flask import Flask, jsonify
import logica
import supermercado_pb2
import supermercado_pb2_grpc

#INTERFAZ REST con Flask
app = Flask(__name__)

@app.route('/producto/<int:prod_id>', methods=['GET'])
def rest_get_producto(prod_id):
    producto = logica.obtener_producto(prod_id) 
    if producto:
        return jsonify({"encontrado": True, **producto}), 200
    return jsonify({"encontrado": False, "mensaje": "No hay datos"}), 404

def iniciar_rest():
    app.run(host='0.0.0.0', port=8000, debug=False, use_reloader=False)

#INTERFAZ gRPC
class InventarioServicer(supermercado_pb2_grpc.InventarioServicer):
    def GetProducto(self, request, context):
        producto = logica.obtener_producto(request.id)
        if producto:
            return supermercado_pb2.ProductoResponse(
                encontrado=True,
                nombre=producto["nombre"],
                descripcion=producto["descripcion"],
                precio=producto["precio"],
                existencias=producto["existencias"]
            )
        return supermercado_pb2.ProductoResponse(encontrado=False)

def iniciar_grpc():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    supermercado_pb2_grpc.add_InventarioServicer_to_server(InventarioServicer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    server.wait_for_termination()

if __name__ == '__main__':
    print("Iniciando servidor REST en puerto 8000...")
    threading.Thread(target=iniciar_rest, daemon=True).start()
    
    print("Iniciando servidor gRPC en puerto 50051...")
    iniciar_grpc()