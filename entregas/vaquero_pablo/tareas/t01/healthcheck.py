import json
from urllib.request import urlopen
import grpc
import catalog_pb2
import catalog_pb2_grpc

with urlopen("http://127.0.0.1:8000/products/1", timeout=2) as response:
    if json.load(response)["id"] != 1:
        raise RuntimeError("REST no está listo")
with grpc.insecure_channel("127.0.0.1:50051") as channel:
    product = catalog_pb2_grpc.CatalogStub(channel).GetProduct(
        catalog_pb2.GetProductRequest(id=1), timeout=2)
    if product.id != 1:
        raise RuntimeError("gRPC no está listo")
