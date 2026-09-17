import grpc

import products_pb2
import products_pb2_grpc
from logica import consult


class ProductsServicer(products_pb2_grpc.ProductsServicer):

    def GetProduct(self, request, context):
        product = consult(request.product_id)
        if product is None:
            context.abort(grpc.StatusCode.NOT_FOUND,
                          "No hay datos para ese ID")
        return products_pb2.ProductResponse(**product)
