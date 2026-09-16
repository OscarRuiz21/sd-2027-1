from concurrent import futures
import grpc
import service_pb2
import service_pb2_grpc

productos = {
    "1": {"id": "1", "nombre": "Laptop", "precio": 999.99}
}

class ProductoServiceServicer(service_pb2_grpc.ProductoServiceServicer):
    def ObtenerProducto(self, request, context):
        prod = productos.get(request.id)
        if prod:
            return service_pb2.ProductoResponse(
                id=prod["id"],
                nombre=prod["nombre"],
                precio=prod["precio"],
                error=""
            )
        return service_pb2.ProductoResponse(error="Producto no encontrado")

    def CrearProducto(self, request, context):
        nuevo_id = str(len(productos) + 1)
        nuevo_prod = {
            "id": nuevo_id,
            "nombre": request.nombre,
            "precio": request.precio
        }
        productos[nuevo_id] = nuevo_prod
        return service_pb2.ProductoResponse(
            id=nuevo_prod["id"],
            nombre=nuevo_prod["nombre"],
            precio=nuevo_prod["precio"],
            error=""
        )

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    service_pb2_grpc.add_ProductoServiceServicer_to_server(ProductoServiceServicer(), server)
    server.add_insecure_port('[::]:50051')
    print("Servidor gRPC corriendo en el puerto 50051...")
    server.start()
    server.wait_for_termination()

if __name__ == '__main__':
    serve()