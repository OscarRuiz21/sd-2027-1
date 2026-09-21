"""Cliente capaz de consultar el mismo servicio por REST y por gRPC."""

import os
import sys
import time

import grpc
import requests

import product_pb2
import product_pb2_grpc

REST_URL = os.getenv("REST_URL", "http://server:8080")
GRPC_ADDRESS = os.getenv("GRPC_ADDRESS", "server:50051")


def esperar_rest(intentos=30):
    for _ in range(intentos):
        try:
            respuesta = requests.get(f"{REST_URL}/health", timeout=1)
            if respuesta.ok:
                return
        except requests.RequestException:
            pass
        time.sleep(1)
    raise RuntimeError("REST no estuvo disponible a tiempo")


def esperar_grpc():
    canal = grpc.insecure_channel(GRPC_ADDRESS)
    grpc.channel_ready_future(canal).result(timeout=30)
    return canal


def consultar_rest(producto_id):
    esperar_rest()
    respuesta = requests.get(
        f"{REST_URL}/products/{producto_id}", timeout=5
    )

    print("\n========== REST ==========")
    print(f"Status: {respuesta.status_code}")
    print(f"Respuesta: {respuesta.json()}")
    print(f"Tamano del cuerpo JSON: {len(respuesta.content)} bytes")


def consultar_grpc(producto_id):
    canal = esperar_grpc()
    cliente = product_pb2_grpc.ProductServiceStub(canal)
    respuesta = cliente.GetProduct(product_pb2.ProductRequest(id=producto_id))

    print("\n========== gRPC ==========")
    print(f"ID: {respuesta.id}")
    print(f"Nombre: {respuesta.nombre or '-'}")
    print(f"Categoria: {respuesta.categoria or '-'}")
    print(f"Disponible: {respuesta.disponible}")
    print(f"Encontrado: {respuesta.encontrado}")
    print(f"Mensaje: {respuesta.mensaje}")
    print(f"Tamano del mensaje protobuf: {respuesta.ByteSize()} bytes")
    canal.close()


def mostrar_uso():
    print("Uso: python client.py [rest|grpc|ambos] [id]")
    print("Ejemplo: python client.py ambos 1")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        mostrar_uso()
        sys.exit(1)

    modo = sys.argv[1].lower()
    try:
        producto_id = int(sys.argv[2])
    except ValueError:
        print("El ID debe ser un numero entero.")
        sys.exit(1)

    try:
        if modo in ("rest", "ambos"):
            consultar_rest(producto_id)
        if modo in ("grpc", "ambos"):
            consultar_grpc(producto_id)
        if modo not in ("rest", "grpc", "ambos"):
            mostrar_uso()
            sys.exit(1)
    except (requests.RequestException, grpc.RpcError, RuntimeError) as error:
        print(f"No se pudo realizar la consulta: {error}")
        sys.exit(1)
