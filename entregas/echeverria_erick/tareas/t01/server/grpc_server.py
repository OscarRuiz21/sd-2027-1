from concurrent import futures
import grpc

import item_pb2
import item_pb2_grpc

from logica import buscar_por_id

class ItemServiceServicer(item_pb2_grpc.ItemServiceServicer):
    def GetItem(self, request, context):
        resultado = buscar_por_id(request.id)

        return item_pb2.ItemResponse(
            encontrado=resultado.get("encontrado", False),
            nombre=resultado.get("nombre", ""),
            area=resultado.get("area", ""),
            mensaje=resultado.get("mensaje", ""),
        )

def iniciar_grpc(puerto):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    item_pb2_grpc.add_ItemServiceServicer_to_server(ItemServiceServicer(), server)
    server.add_insecure_port(f"0.0.0.0:{puerto}")
    server.start()
    print(f"gRPC escuchando en puerto {puerto}")
    server.wait_for_termination()