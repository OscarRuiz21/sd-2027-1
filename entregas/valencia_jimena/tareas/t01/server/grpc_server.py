from concurrent import futures
import grpc

import item_pb2
import item_pb2_grpc
from service import get_item_by_id


class ItemServicer(item_pb2_grpc.ItemServiceServicer):
    def GetItem(self, request, context):
        result = get_item_by_id(request.id)

        if not result["found"]:
            print(f"[gRPC] GetItem({request.id}) -> found=False")
            return item_pb2.ItemResponse(found=False, id=request.id)

        item = result["item"]
        print(f"[gRPC] GetItem({request.id}) -> found=True")
        return item_pb2.ItemResponse(
            found=True,
            id=item["id"],
            nombre=item["nombre"],
            tipo=item["tipo"],
            activo=item["activo"],
        )


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    item_pb2_grpc.add_ItemServiceServicer_to_server(ItemServicer(), server)
    server.add_insecure_port("0.0.0.0:50051")
    server.start()
    print("[gRPC] escuchando en puerto 50051")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()