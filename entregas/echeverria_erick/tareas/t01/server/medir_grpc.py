import item_pb2

respuesta = item_pb2.ItemResponse(
    encontrado=True,
    nombre="Pedro Lopez",
    area="Computacion",
    mensaje=""
)

datos_binarios = respuesta.SerializeToString()
print("Bytes del mensaje gRPC:", len(datos_binarios))
