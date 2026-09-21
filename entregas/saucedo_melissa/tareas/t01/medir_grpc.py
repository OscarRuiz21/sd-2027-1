# medir_grpc.py
# Mide cuantos bytes pesa el mensaje serializado que gRPC manda por la red,
# para compararlo contra los bytes que vimos con curl en REST.

import servicio_pb2

peticion = servicio_pb2.ItemRequest(id="1")
respuesta = servicio_pb2.ItemResponse(
    encontrado=True,
    nombre="Melissa Saucedo",
    carrera="Ingenieria en Computacion",
)

bytes_peticion = peticion.SerializeToString()
bytes_respuesta = respuesta.SerializeToString()

print(f"Peticion (ItemRequest) serializada: {len(bytes_peticion)} bytes")
print(f"Respuesta (ItemResponse) serializada: {len(bytes_respuesta)} bytes")
print(f"Total ida y vuelta: {len(bytes_peticion) + len(bytes_respuesta)} bytes")