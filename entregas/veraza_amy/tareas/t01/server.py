from concurrent import futures
from http.server import ThreadingHTTPServer
import grpc
import catalog_pb2_grpc
from grpc_server import Catalog
from rest_server import Handler

if __name__ == '__main__':
    rpc_server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    catalog_pb2_grpc.add_CatalogServicer_to_server(Catalog(), rpc_server)
    rpc_server.add_insecure_port('0.0.0.0:50051')
    rpc_server.start()
    print('Catalogo compartido: REST :8080 y gRPC :50051', flush=True)
    try:
        ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
    finally:
        rpc_server.stop(2).wait()
