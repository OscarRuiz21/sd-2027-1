import requests
import grpc
import supermercado_pb2
import supermercado_pb2_grpc

def llamar_rest(prod_id):
    print(f"\n--- Petición REST para ID {prod_id} ---")
    url = f"http://server:8000/producto/{prod_id}"
    respuesta = requests.get(url)
    if respuesta.status_code == 200:
        print("Respuesta:", respuesta.json())
    else:
        print("Respuesta:", respuesta.json())

def llamar_grpc(prod_id):
    print(f"\n--- Petición gRPC para ID {prod_id} ---")
    with grpc.insecure_channel('server:50051') as channel:
        stub = supermercado_pb2_grpc.InventarioStub(channel)
        request = supermercado_pb2.ProductoRequest(id=prod_id)
        respuesta = stub.GetProducto(request)
        if respuesta.encontrado:
            print(f"Respuesta: nombre='{respuesta.nombre}', descripcion='{respuesta.descripcion}', "
                  f"precio={respuesta.precio}, existencias={respuesta.existencias}")
        else:
            print("Respuesta: No hay datos")

if __name__ == '__main__':
    while True:
        try:
            prod_id = int(input("\nIngresa el ID del producto a buscar (0 para salir): "))
            if prod_id == 0: break
            
            print("Selecciona el protocolo:")
            print("1. REST")
            print("2. gRPC")
            print("3. Ambos")
            opcion = input("Opción: ")
            
            if opcion in ['1', '3']: llamar_rest(prod_id)
            if opcion in ['2', '3']: llamar_grpc(prod_id)
            
        except ValueError:
            print("Por favor, ingresa un número válido.")