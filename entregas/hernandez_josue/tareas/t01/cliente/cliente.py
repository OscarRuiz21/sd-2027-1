import argparse
import json
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import urlopen

import grpc

import catalogo_pb2
import catalogo_pb2_grpc


def consultar_rest(identificador):
    consulta = urlencode({"id": identificador})
    try:
        with urlopen(f"http://servidor:8080/libros?{consulta}", timeout=5) as respuesta:
            cuerpo = respuesta.read()
            libro = json.loads(cuerpo)
            print(f"REST {respuesta.status}: {libro} ({len(cuerpo)} bytes de JSON)")
            return libro
    except HTTPError as error:
        if error.code != 404:
            raise
        with error:
            detalle = json.load(error)["error"]
        print(f"REST 404: {detalle}")
        return None


def consultar_grpc(catalogo, identificador):
    try:
        respuesta = catalogo.Consultar(catalogo_pb2.Consulta(id=identificador), timeout=5)
    except grpc.RpcError as error:
        if error.code() != grpc.StatusCode.NOT_FOUND:
            raise
        print(f"gRPC NOT_FOUND: {error.details()}")
        return None

    libro = {"id": respuesta.id, "titulo": respuesta.titulo, "anio": respuesta.anio}
    print(f"gRPC OK: {libro} ({respuesta.ByteSize()} bytes de Protobuf)")
    return libro


def main():
    parser = argparse.ArgumentParser(description="Consulta el catálogo por REST y gRPC.")
    parser.add_argument("ids", nargs="*", help="IDs de los libros que quieres consultar")
    opciones = parser.parse_args()

    with grpc.insecure_channel("servidor:50051") as canal:
        catalogo = catalogo_pb2_grpc.CatalogoStub(canal)
        for identificador in opciones.ids or ("L01", "L99"):
            print(f"\nConsulta de {identificador}")
            rest = consultar_rest(identificador)
            grpc_resultado = consultar_grpc(catalogo, identificador)
            if rest != grpc_resultado:
                raise SystemExit("Los datos de las dos respuestas no coinciden.")


if __name__ == "__main__":
    main()
