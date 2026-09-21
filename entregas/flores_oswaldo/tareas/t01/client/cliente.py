import os
import sys

import grpc
import requests

import service_pb2
import service_pb2_grpc

HOST = os.environ.get("SERVIDOR", "localhost")


def por_rest(id_):
    respuesta = requests.get(f"http://{HOST}:5000/items/{id_}")
    return respuesta.json()


def por_grpc(id_):
    with grpc.insecure_channel(f"{HOST}:50051") as canal:
        stub = service_pb2_grpc.ConsultaStub(canal)
        r = stub.Buscar(service_pb2.BuscarRequest(id=id_))
    if not r.encontrado:
        return {"encontrado": False}
    return {
        "encontrado": True,
        "nombre": r.nombre,
        "rol": r.rol,
        "edad": r.edad,
    }


if __name__ == "__main__":
    ids = sys.argv[1:] or ["1", "99"]
    for id_ in ids:
        print(f"--- ID {id_} ---")
        print("REST:", por_rest(id_))
        print("gRPC:", por_grpc(id_))