
from concurrent import futures

import grpc

import product_pb2
import product_pb2_grpc
from service import buscar_producto


class ProductService(product_pb2_grpc.ProductServiceServicer):
    def GetProduct(self, request, context):
        producto = buscar_producto(request.id)

        if producto is None:
            return product_pb2.ProductResponse(
                id=request.id,
                encontrado=False,
                mensaje="No hay datos para ese ID",
            )

        return product_pb2.ProductResponse(
            id=producto["id"],
            nombre=producto["nombre"],
            categoria=producto["categoria"],
            disponible=producto["disponible"],
            encontrado=True,
            mensaje="Producto encontrado",
        )


def iniciar_grpc():
    servidor = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    product_pb2_grpc.add_ProductServiceServicer_to_server(
        ProductService(), servidor
    )
    servidor.add_insecure_port("[::]:50051")
    servidor.start()
    print("gRPC disponible en el puerto 50051", flush=True)
    servidor.wait_for_termination()
