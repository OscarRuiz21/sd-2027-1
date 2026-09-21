import grpc
import requests

import servicio_pb2
import servicio_pb2_grpc


USUARIO_ID = 1


# Medicion REST
respuesta_rest = requests.get(
    f"http://localhost:5001/usuarios/{USUARIO_ID}",
    timeout=5
)

respuesta_rest.raise_for_status()

bytes_rest = len(respuesta_rest.content)


# Medicion gRPC
with grpc.insecure_channel("localhost:50051") as canal:
    cliente = servicio_pb2_grpc.UsuarioServiceStub(canal)

    respuesta_grpc = cliente.ObtenerUsuario(
        servicio_pb2.UsuarioRequest(id=USUARIO_ID)
    )

    bytes_grpc = len(respuesta_grpc.SerializeToString())


# Resultados
print("Medicion para la misma consulta: ID 1")
print()
print(f"REST - cuerpo de respuesta JSON: {bytes_rest} bytes")
print(f"gRPC - mensaje Protocol Buffers: {bytes_grpc} bytes")
print()
print("La medicion corresponde solamente al contenido de la respuesta.")
print("No incluye cabeceras HTTP, TCP ni otros datos del transporte.")
