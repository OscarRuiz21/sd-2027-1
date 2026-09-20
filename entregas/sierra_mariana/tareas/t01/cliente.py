import requests
import grpc
import time
import servicio_pb2
import servicio_pb2_grpc

# Pausa breve para asegurar que el servidor levantó en Docker
time.sleep(3)

id_test = 1

print("\n--- PRUEBA REST ---")
res_rest = requests.get(f'http://servidor:8080/info/{id_test}')
bytes_rest = len(res_rest.content)
print(f"Respuesta: {res_rest.json()} | Tamaño: {bytes_rest} bytes")

print("\n--- PRUEBA gRPC ---")
canal = grpc.insecure_channel('servidor:50051')
stub = servicio_pb2_grpc.ServicioInfoStub(canal)
res_grpc = stub.ObtenerInfo(servicio_pb2.InfoRequest(id=id_test))
bytes_grpc = res_grpc.ByteSize()
print(f"Respuesta: {res_grpc.datos} | Tamaño: {bytes_grpc} bytes\n")