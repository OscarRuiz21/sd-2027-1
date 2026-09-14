import threading
from concurrent import futures
import grpc
from fastapi import FastAPI, HTTPException
import uvicorn

import servicio_pb2
import servicio_pb2_grpc
from service import consultar_item

app = FastAPI(title="Servicio Hibrido REST y gRPC")

@app.get("/items/{item_id}")
def get_item_rest(item_id: str):
    resultado = consultar_item(item_id)
    if not resultado["encontrado"]:
        raise HTTPException(status_code=404, detail="Item no encontrado")
    return resultado

class ItemServiceImpl(servicio_pb2_grpc.ItemServiceServicer):
    def GetItem(self, request, context):
        resultado = consultar_item(request.id)
        if not resultado["encontrado"]:
            context.set_code(grpc.StatusCode.NOT_FOUND)
            context.set_details("Item no encontrado")
            return servicio_pb2.ItemResponse(
                id=request.id,
                nombre="",
                detalle="Item no encontrado",
                encontrado=False
            )
        return servicio_pb2.ItemResponse(
            id=resultado["id"],
            nombre=resultado["nombre"],
            detalle=resultado["detalle"],
            encontrado=True
        )

def run_grpc_server():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=5))
    servicio_pb2_grpc.add_ItemServiceServicer_to_server(ItemServiceImpl(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    server.wait_for_termination()

if __name__ == "__main__":
    grpc_thread = threading.Thread(target=run_grpc_server, daemon=True)
    grpc_thread.start()
    uvicorn.run(app, host="0.0.0.0", port=8000)
