"""Cliente único; compara datos y estados equivalentes en ambos transportes."""
import argparse
import json
import os
from urllib.error import HTTPError
from urllib.request import urlopen

import grpc
import catalog_pb2
import catalog_pb2_grpc


def call_rest(base_url, product_id):
    try:
        response = urlopen(f"{base_url}/products/{product_id}", timeout=5)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read()
        status = response.code
    meanings = {200: "OK", 400: "INVALID_ARGUMENT", 404: "NOT_FOUND"}
    if status not in meanings:
        raise RuntimeError(f"HTTP inesperado: {status}")
    return meanings[status], json.loads(raw), len(raw), str(status)


def call_grpc(target, product_id):
    with grpc.insecure_channel(target) as channel:
        stub = catalog_pb2_grpc.CatalogStub(channel)
        try:
            response = stub.GetProduct(catalog_pb2.GetProductRequest(id=product_id), timeout=5)
            data = {"id": response.id, "name": response.name, "price_cents": response.price_cents}
            return "OK", data, response.ByteSize(), "OK"
        except grpc.RpcError as error:
            if error.code() not in (grpc.StatusCode.INVALID_ARGUMENT, grpc.StatusCode.NOT_FOUND):
                raise
            return error.code().name, {"error": error.details()}, None, error.code().name


def int32(value):
    number = int(value)
    if not -(2**31) <= number <= 2**31 - 1:
        raise argparse.ArgumentTypeError("El ID debe caber en int32")
    return number


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol", choices=["both", "rest", "grpc"], default="both")
    parser.add_argument("--ids", nargs="+", type=int32, default=[1, 2, 999, 0])
    parser.add_argument("--rest-url", default=os.getenv("REST_URL", "http://localhost:8000"))
    parser.add_argument("--grpc-target", default=os.getenv("GRPC_TARGET", "localhost:50051"))
    args = parser.parse_args()
    for product_id in args.ids:
        results = []
        print(f"\nID {product_id}")
        if args.protocol in ("both", "rest"):
            results.append(("REST", call_rest(args.rest_url, product_id)))
        if args.protocol in ("both", "grpc"):
            results.append(("gRPC", call_grpc(args.grpc_target, product_id)))
        for protocol, (meaning, data, size, status) in results:
            print(f"{protocol}: {status} | {json.dumps(data, ensure_ascii=False)}")
            if size is not None:
                print(f"  Cuerpo de respuesta: {size} bytes (sin cabeceras ni transporte)")
        if len(results) == 2:
            if results[0][1][:2] != results[1][1][:2]:
                raise RuntimeError(f"Las interfaces no coinciden para ID {product_id}")
            print("Coinciden el resultado y el significado del estado: SÍ")


if __name__ == "__main__":
    main()
