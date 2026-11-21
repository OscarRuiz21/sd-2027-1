import json
import os
from urllib.request import urlopen
from urllib.error import HTTPError
import grpc
import catalogo_pb2 as pb
import catalogo_pb2_grpc as rpc

host = os.getenv("SERVIDOR", "localhost")
with grpc.insecure_channel(host+":50051") as channel:
    grpc.channel_ready_future(channel).result(timeout=45)
    stub = rpc.CatalogoStub(channel)
    for ident in (1, 2, 999):
        try:
            with urlopen(f"http://{host}:8080/productos/{ident}", timeout=10) as response:
                raw = response.read()
                rest = json.loads(raw)
                status = response.status
        except HTTPError as error:
            status, rest = error.code, json.load(error)
        try:
            result = stub.Consultar(pb.Consulta(id=ident), timeout=10)
            remote = {"id": result.id, "nombre": result.nombre, "precio_centavos": result.precio_centavos}
            assert status == 200 and rest == remote
            print(f"ID {ident}: REST 200 {rest}; gRPC OK {remote}")
            print(f"Cuerpos serializados: JSON {len(raw)} bytes; Protobuf {len(result.SerializeToString())} bytes (NO incluye red ni cabeceras)")
        except grpc.RpcError as error:
            assert status == 404 and error.code() == grpc.StatusCode.NOT_FOUND
            print(f"ID {ident}: REST 404; gRPC NOT_FOUND; mismo caso sin datos")
print("PASS: dos interfaces, mismos resultados y mismo error de negocio")
