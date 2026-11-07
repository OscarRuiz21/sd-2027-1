from concurrent import futures

import grpc

import catalogo_pb2
import catalogo_pb2_grpc
import nucleo


class ServicioCatalogo(catalogo_pb2_grpc.CatalogoServicer):

    def Consultar(self, peticion, contexto):
        try:
            articulo = nucleo.consultar_articulo(peticion.id)
        except nucleo.ArticuloNoEncontrado as e:
            contexto.abort(grpc.StatusCode.NOT_FOUND, str(e))
            return
        return catalogo_pb2.Articulo(**articulo)

    def Listar(self, peticion, contexto):
        return catalogo_pb2.ListaIds(ids=nucleo.listar_ids())


def construir_servidor(puerto: int, hilos: int = 8) -> grpc.Server:
    servidor = grpc.server(futures.ThreadPoolExecutor(max_workers=hilos))
    catalogo_pb2_grpc.add_CatalogoServicer_to_server(ServicioCatalogo(), servidor)
    servidor.add_insecure_port(f"0.0.0.0:{puerto}")
    return servidor
