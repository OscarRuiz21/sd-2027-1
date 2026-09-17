"""Mide, en la capa de aplicación, cuántos bytes viajan en la misma llamada 
por REST y por gRPC."""

import os

import grpc
import requests

import products_pb2
import products_pb2_grpc

REST_URL = os.getenv("REST_URL", "http://localhost:8000")
GRPC_TARGET = os.getenv("GRPC_TARGET", "localhost:50051")
PRODUCT_ID = 2


def medir_rest():
    resp = requests.get(f"{REST_URL}/products/{PRODUCT_ID}")
    req = resp.request
    linea = f"{req.method} {req.path_url} HTTP/1.1\r\n"
    cabeceras = "".join(f"{k}: {v}\r\n" for k, v in req.headers.items())
    bytes_peticion = len((linea + cabeceras + "\r\n").encode())

    linea = f"HTTP/1.1 {resp.status_code} {resp.reason}\r\n"
    cabeceras = "".join(f"{k}: {v}\r\n" for k, v in resp.headers.items())
    bytes_respuesta = len(
        (linea + cabeceras + "\r\n").encode()) + len(resp.content)
    return bytes_peticion, bytes_respuesta, len(resp.content)


def medir_grpc():
    stub = products_pb2_grpc.ProductsStub(grpc.insecure_channel(GRPC_TARGET))
    peticion = products_pb2.ProductRequest(product_id=PRODUCT_ID)
    respuesta = stub.GetProduct(peticion)
    # gRPC antepone 1 byte (compresión) + 4 bytes (longitud) a cada mensaje
    MARCO = 5
    return peticion.ByteSize() + MARCO, respuesta.ByteSize() + MARCO, respuesta.ByteSize()


if __name__ == "__main__":
    r_pet, r_resp, r_cuerpo = medir_rest()
    g_pet, g_resp, g_cuerpo = medir_grpc()
    print(f"Llamada: producto {PRODUCT_ID}\n")
    print(f"{'':10} {'petición':>10} {'respuesta':>10} {'total':>8}   (solo datos)")
    print(f"{'REST':10} {r_pet:>10} {r_resp:>10} {r_pet + r_resp:>8}   ({r_cuerpo} B de JSON)")
    print(f"{'gRPC':10} {g_pet:>10} {g_resp:>10} {g_pet + g_resp:>8}   ({g_cuerpo} B de protobuf)")
    print("\nREST: línea + cabeceras HTTP/1.1 + cuerpo JSON.")
    print("gRPC: mensaje protobuf + marco de 5 bytes. No incluye las cabeceras HTTP/2.")
