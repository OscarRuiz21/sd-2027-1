import concurrent.futures
import threading
import grpc
from fastapi import FastAPI, HTTPException
import uvicorn

import service
import service_pb2
import service_pb2_grpc

class UsuarioGRPCServicer(service_pb2_grpc.UsuarioServiceServicer):
    def ObtenerUsuario(self, request, context):
        resultado = service.buscar_usuario_por_id(request.id)
        return service_pb2.UsuarioResponse(
            id=resultado["id"],
            nombre=resultado["nombre"],
            email=resultado["email"],
            encontrado=resultado["encontrado"]
        )

def iniciar_grpc():
    server = grpc.server(concurrent.futures.ThreadPoolExecutor(max_workers=10))
    service_pb2_grpc.add_UsuarioServiceServicer_to_server(UsuarioGRPCServicer(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    print("Servidor gRPC corriendo en el puerto 50051")
    server.wait_for_termination()

app = FastAPI()

@app.get("/usuarios/{usuario_id}")
def get_usuario(usuario_id: int):
    resultado = service.buscar_usuario_por_id(usuario_id)
    if not resultado["encontrado"]:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    return resultado

if __name__ == "__main__":
    threading.Thread(target=iniciar_grpc, daemon=True).start()
    print("Servidor REST corriendo en el puerto 8000")
    uvicorn.run(app, host="0.0.0.0", port=8000)