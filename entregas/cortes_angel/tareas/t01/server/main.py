import threading
import grpc
from concurrent import futures
from fastapi import FastAPI
from fastapi.responses import JSONResponse
import uvicorn

# Importamos los archivos autogenerados de gRPC
import servicio_pb2
import servicio_pb2_grpc


CATALOGO = {
    "101": {"informacion": "Laptop Dell XPS 15 - 16GB RAM, 512GB SSD"},
    "103": {"informacion": "Monitor LG 27 pulgadas 4K"}
}

def buscar_item(item_id: str) -> dict:
    """Esta es la única lógica de negocio del sistema"""
    if item_id in CATALOGO:
        return {"id": item_id, "informacion": CATALOGO[item_id]["informacion"], "encontrado": True}
    return {"id": item_id, "informacion": "No se encontraron datos", "encontrado": False}


app = FastAPI()

@app.get("/api/consulta/{item_id}")
def consulta_rest(item_id: str):
    resultado = buscar_item(item_id) # Llama a la lógica central
    
    # Si no se encuentra, devolvemos un estado HTTP 404
    if not resultado["encontrado"]:
        return JSONResponse(status_code=404, content=resultado)
    return resultado



class BuscadorServicer(servicio_pb2_grpc.BuscadorServiceServicer):
    def Consultar(self, request, context):
        resultado = buscar_item(request.id) # Llama a la MISMA lógica central
        
        # Convierte el diccionario de Python a la estructura binaria de Protobuf
        return servicio_pb2.ConsultaResponse(
            id=resultado["id"],
            informacion=resultado["informacion"],
            encontrado=resultado["encontrado"]
        )

def serve_grpc():
    """Configura y levanta el servidor gRPC en el puerto 50051"""
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    servicio_pb2_grpc.add_BuscadorServiceServicer_to_server(BuscadorServicer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    server.wait_for_termination()



if __name__ == '__main__':
    # Arranca gRPC en un hilo secundario para que no bloquee el programa
    grpc_thread = threading.Thread(target=serve_grpc, daemon=True)
    grpc_thread.start()
    print("Servidor gRPC escuchando en el puerto 50051...")

    # Arranca REST en el hilo principal
    print("Servidor REST escuchando en el puerto 3000...")
    uvicorn.run(app, host="0.0.0.0", port=3000)