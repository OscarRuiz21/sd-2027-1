import grpc
import service_pb2
import service_pb2_grpc

# Conectar al servidor gRPC
channel = grpc.insecure_channel('localhost:50051')
stub = service_pb2_grpc.ProductoServiceStub(channel)

# Probar Obtener Producto con ID "1"
print("Enviando petición gRPC para obtener el producto 1...")
response = stub.ObtenerProducto(service_pb2.ProductoRequest(id="1"))
print("Respuesta recibida del servidor gRPC:", response)