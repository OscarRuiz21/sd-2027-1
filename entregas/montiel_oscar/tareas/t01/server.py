"""Servidor que expone la misma lógica de catálogo por REST y por gRPC."""

from concurrent import futures

import grpc
import uvicorn
from fastapi import FastAPI, HTTPException

import catalog_pb2
import catalog_pb2_grpc


class CatalogService:
    """Contiene la lógica de negocio compartida por los dos protocolos."""

    def __init__(self) -> None:
        """Inicializa una fuente de datos deliberadamente simple en memoria."""
        self._items = {
            "1": {"id": "1", "name": "Teclado mecánico", "price": 899.0},
            "2": {"id": "2", "name": "Mouse inalámbrico", "price": 399.0},
            "3": {"id": "3", "name": "Monitor", "price": 3299.0},
        }

    def get_item(self, item_id: str) -> dict | None:
        """Devuelve el artículo asociado al ID o None cuando no hay datos."""
        return self._items.get(item_id)


catalog_service = CatalogService()
app = FastAPI(title="T01 Catalog Service")


@app.get("/items/{item_id}")
def get_item_rest(item_id: str) -> dict:
    """Actúa como controlador REST y delega en la lógica compartida."""
    item = catalog_service.get_item(item_id)

    if item is None:
        raise HTTPException(status_code=404, detail="No hay datos para ese ID")

    return item


class CatalogGrpcController(catalog_pb2_grpc.CatalogServicer):
    """Actúa como controlador gRPC y delega en la lógica compartida."""

    def GetItem(
        self,
        request: catalog_pb2.ItemRequest,
        context: grpc.ServicerContext,
    ) -> catalog_pb2.ItemReply:
        """Convierte la petición gRPC en una consulta a CatalogService."""
        item = catalog_service.get_item(request.id)

        if item is None:
            context.set_code(grpc.StatusCode.NOT_FOUND)
            context.set_details("No hay datos para ese ID")
            return catalog_pb2.ItemReply()

        return catalog_pb2.ItemReply(
            id=item["id"],
            name=item["name"],
            price=item["price"],
        )


def start_grpc_server() -> grpc.Server:
    """Inicia gRPC en el puerto 50051 y devuelve su instancia."""
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    catalog_pb2_grpc.add_CatalogServicer_to_server(CatalogGrpcController(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    return server


if __name__ == "__main__":
    grpc_server = start_grpc_server()

    try:
        uvicorn.run(app, host="0.0.0.0", port=8000)
    finally:
        grpc_server.stop(grace=0)
