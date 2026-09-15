import sys
import requests
import grpc

sys.path.insert(0, "client")

import service_pb2
import service_pb2_grpc


ID_USUARIO = 1


# -------------------------
# REST
# -------------------------

respuesta_rest = requests.get(
    f"http://localhost:5000/usuarios/{ID_USUARIO}"
)

bytes_rest = len(respuesta_rest.content)

print("REST")
print("Respuesta:", respuesta_rest.json())
print("Bytes recibidos:", bytes_rest)


# -------------------------
# gRPC
# -------------------------

with grpc.insecure_channel("localhost:50051") as channel:
    stub = service_pb2_grpc.UsuarioServiceStub(channel)

    respuesta_grpc = stub.ObtenerUsuario(
        service_pb2.UsuarioRequest(id=ID_USUARIO)
    )

    datos_grpc = respuesta_grpc.SerializeToString()
    bytes_grpc = len(datos_grpc)

    print("\ngRPC")
    print(
        "Respuesta:",
        {
            "id": respuesta_grpc.id,
            "nombre": respuesta_grpc.nombre,
            "edad": respuesta_grpc.edad
        }
    )
    print("Bytes recibidos:", bytes_grpc)