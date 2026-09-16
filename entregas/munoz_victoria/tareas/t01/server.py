from concurrent import futures
import time
import grpc
from fastapi import FastAPI, HTTPException
import uvicorn
import threading

# Importar los archivos generados del proto y la base de datos
import service_pb2
import service_pb2_grpc
from database import get_item_from_db

# 1. IMPLEMENTACIÓN DE gRPC
class ItemServiceServicer(service_pb2_grpc.ItemServiceServicer):
    def GetItem(self, request, context):
        item = get_item_from_db(request.id)
        if not item:
            context.set_code(grpc.StatusCode.NOT_FOUND)
            context.set_details("Item no encontrado")
            return service_pb2.ItemResponse()
        
        return service_pb2.ItemResponse(
            id=item["id"],
            name=item["name"],
            description=item["description"],
            price=item["price"]
        )

def serve_grpc():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    service_pb2_grpc.add_ItemServiceServicer_to_server(ItemServiceServicer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    print("Servidor gRPC corriendo en el puerto 50051...")
    server.wait_for_termination()

# 2. IMPLEMENTACIÓN DE REST (FastAPI)
app = FastAPI(title="Servicio Híbrido REST y gRPC")

@app.get("/items/{item_id}")
def get_item_rest(item_id: str):
    item = get_item_from_db(item_id)
    if not item:
        raise HTTPException(status_code=404, detail="Item no encontrado")
    return item

# 3. LANZAR AMBOS EN PARALELO
if __name__ == "__main__":
    # Iniciar gRPC en un hilo secundario
    grpc_thread = threading.Thread(target=serve_grpc, daemon=True)
    grpc_thread.start()

    # Iniciar FastAPI (REST) en el hilo principal
    uvicorn.run(app, host="0.0.0.0", port=8000)