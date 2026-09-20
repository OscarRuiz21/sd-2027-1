import grpc
import requests
import sys

# Importa los contratos de gRPC
import servicio_pb2
import servicio_pb2_grpc

def probar_rest(item_id):
    print(f"\n--- Prueba REST (ID: {item_id}) ---")
    response = requests.get(f"http://server:3000/api/consulta/{item_id}")
    
    print(f"Status HTTP: {response.status_code}")
    print(f"Cuerpo JSON: {response.text}")
    
    # Mide el peso del cuerpo de la respuesta
    peso = len(response.content)
    print(f"Peso de la respuesta (Capa de Aplicación): {peso} bytes")

def probar_grpc(item_id):
    print(f"\n--- Prueba gRPC (ID: {item_id}) ---")
    # conecta al puerto 50051 donde escucha gRPC
    with grpc.insecure_channel('server:50051') as channel:
        stub = servicio_pb2_grpc.BuscadorServiceStub(channel)
        respuesta = stub.Consultar(servicio_pb2.ConsultaRequest(id=item_id))
        
        print(f"Datos recibidos: ID={respuesta.id}, Info='{respuesta.informacion}', Encontrado={respuesta.encontrado}")
        
        
        peso = respuesta.ByteSize()
        print(f"Peso de la respuesta (Capa de Aplicación): {peso} bytes")

if __name__ == '__main__':
    print(">>> BUSCANDO UN ID QUE SÍ EXISTE (101)")
    probar_rest("101")
    probar_grpc("101")
    
    print("\n>>> BUSCANDO UN ID QUE NO EXISTE (999)")
    probar_rest("999")
    probar_grpc("999")