# cliente.py
# Un solo cliente que sabe llamar al servicio de dos formas distintas:
# por REST (HTTP + JSON) y por gRPC (protobuf binario).
# Sirve para demostrar que ambas interfaces exponen la misma logica.
# El host del servidor viene de una variable de entorno, para que el
# mismo codigo funcione tanto en mi maquina (localhost) como en Docker
# (donde el servidor se llama "server", por el nombre del servicio).

import os
import sys
import requests
import grpc

import servicio_pb2
import servicio_pb2_grpc

SERVIDOR_HOST = os.environ.get("SERVIDOR_HOST", "localhost")


def llamar_rest(item_id):
    url = f"http://{SERVIDOR_HOST}:5000/item/{item_id}"
    respuesta = requests.get(url)
    print(f"[REST] status={respuesta.status_code} body={respuesta.text}")


def llamar_grpc(item_id):
    canal = grpc.insecure_channel(f"{SERVIDOR_HOST}:50051")
    stub = servicio_pb2_grpc.ServicioItemsStub(canal)
    peticion = servicio_pb2.ItemRequest(id=item_id)
    respuesta = stub.ObtenerItem(peticion)
    print(f"[gRPC] encontrado={respuesta.encontrado} nombre={respuesta.nombre} carrera={respuesta.carrera}")


if __name__ == "__main__":
    item_id = sys.argv[1] if len(sys.argv) > 1 else "1"
    print(f"--- Pidiendo el id={item_id} por las dos interfaces (servidor: {SERVIDOR_HOST}) ---")
    llamar_rest(item_id)
    llamar_grpc(item_id)