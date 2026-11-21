import threading
import grpc
from concurrent import futures
from fastapi import FastAPI
import uvicorn
import servicio_pb2
import servicio_pb2_grpc
from logica import consultar_datos

# --- Controlador gRPC ---
class InfoServiceServicer(servicio_pb2_grpc.InfoServiceServicer):
    def ObtenerInfo(self, request, context):
        resultado = consultar_datos(request.id)
        return servicio_pb2.InfoResponse(
            id=resultado["id"], 
            datos=resultado["datos"], 
            error=resultado["error"]
        )

def iniciar_grpc():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    servicio_pb2_grpc.add_InfoServiceServicer_to_server(InfoServiceServicer(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    server.wait_for_termination()

# --- Controlador REST (FastAPI) ---
app = FastAPI()

@app.get("/info/{id}")
def obtener_info_rest(id: str):
    return consultar_datos(id)

def iniciar_rest():
    uvicorn.run(app, host="0.0.0.0", port=8000)

if __name__ == '__main__':
    # Levantamos gRPC en un hilo secundario
    hilo_grpc = threading.Thread(target=iniciar_grpc, daemon=True)
    hilo_grpc.start()
    
    # Levantamos REST en el hilo principal
    iniciar_rest()
