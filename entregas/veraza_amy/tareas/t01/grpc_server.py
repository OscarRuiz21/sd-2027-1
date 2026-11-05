from concurrent import futures

import grpc
import catalog_pb2
import catalog_pb2_grpc
from catalog import get_product


class Catalog(catalog_pb2_grpc.CatalogServicer):
    def GetProduct(self, request, context):
        try:
            return catalog_pb2.Product(**get_product(request.id))
        except ValueError as error:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(error))
        except KeyError as error:
            context.abort(grpc.StatusCode.NOT_FOUND, error.args[0])


if __name__ == "__main__":
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    catalog_pb2_grpc.add_CatalogServicer_to_server(Catalog(), server)
    server.add_insecure_port("0.0.0.0:50051")
    server.start()
    print("gRPC escuchando en 0.0.0.0:50051", flush=True)
    server.wait_for_termination()
