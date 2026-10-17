import sys
import requests
import grpc
import servicio_pb2
import servicio_pb2_grpc

def llamar_rest(id_consulta):
    url = f"http://servidor:8000/info/{id_consulta}"
    print(f"Llamando REST: {url}")
    respuesta = requests.get(url)
    print("Respuesta REST:", respuesta.json())

def llamar_grpc(id_consulta):
    canal = grpc.insecure_channel('servidor:50051')
    stub = servicio_pb2_grpc.InfoServiceStub(canal)
    solicitud = servicio_pb2.InfoRequest(id=id_consulta)
    
    print(f"Llamando gRPC a servidor:50051 con ID {id_consulta}")
    respuesta = stub.ObtenerInfo(solicitud)
    print("Respuesta gRPC:", {"id": respuesta.id, "datos": respuesta.datos, "error": respuesta.error})

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Uso: python cliente.py [rest|grpc] [id]")
        sys.exit(1)
    
    protocolo = sys.argv[1].lower()
    id_consulta = sys.argv[2]

    if protocolo == "rest":
        llamar_rest(id_consulta)
    elif protocolo == "grpc":
        llamar_grpc(id_consulta)
    else:
        print("Protocolo inválido. Usa 'rest' o 'grpc'.")