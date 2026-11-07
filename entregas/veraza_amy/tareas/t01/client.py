import argparse
import http.client
import json
import os
import grpc
import catalog_pb2 as pb
import catalog_pb2_grpc as rpc

parser = argparse.ArgumentParser(description='Consulta el mismo producto por REST y gRPC')
parser.add_argument('id', type=int, nargs='?', default=1)
args = parser.parse_args()
if not -2147483648 <= args.id <= 2147483647:
    parser.error('El ID debe caber en int32')
connection = http.client.HTTPConnection(os.getenv('REST_HOST', 'localhost'), 8080, timeout=5)
try:
    connection.request('GET', f'/products/{args.id}')
    response = connection.getresponse()
    print(f'REST HTTP {response.status}: {response.read().decode()}', flush=True)
finally:
    connection.close()
with grpc.insecure_channel(os.getenv('GRPC_HOST', 'localhost') + ':50051') as channel:
    try:
        product = rpc.CatalogStub(channel).GetProduct(pb.GetProductRequest(id=args.id), timeout=5)
        print('gRPC OK: ' + json.dumps(dict(id=product.id, name=product.name, price_cents=product.price_cents, stock=product.stock)))
    except grpc.RpcError as error:
        print(f'gRPC {error.code().name}: {error.details()}')
