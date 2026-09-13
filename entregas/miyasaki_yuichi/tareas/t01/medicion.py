import json
import os
import socket
import threading
import time

import grpc
import requests

import catalogo_pb2
import catalogo_pb2_grpc

HOST = os.getenv("SERVIDOR_HOST", "servidor")
PUERTO_REST = int(os.getenv("PUERTO_REST", "8000"))
PUERTO_GRPC = int(os.getenv("PUERTO_GRPC", "50051"))
ID_PRUEBA = os.getenv("ID_PRUEBA", "A-100")
ID_FALLO = os.getenv("ID_FALLO", "Z-999")


class ProxyMedidor:

    def __init__(self, host_destino: str, puerto_destino: int):
        self.destino = (host_destino, puerto_destino)
        self._lock = threading.Lock()
        self.subida = 0
        self.bajada = 0

        self._escucha = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._escucha.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._escucha.bind(("127.0.0.1", 0))
        self._escucha.listen(16)
        self.puerto = self._escucha.getsockname()[1]
        threading.Thread(target=self._aceptar, daemon=True).start()

    @property
    def destino_local(self) -> str:
        return f"127.0.0.1:{self.puerto}"

    def reiniciar(self):
        with self._lock:
            self.subida = self.bajada = 0

    def leer(self) -> tuple[int, int]:
        time.sleep(0.25)
        with self._lock:
            return self.subida, self.bajada

    def _sumar(self, es_subida: bool, n: int):
        with self._lock:
            if es_subida:
                self.subida += n
            else:
                self.bajada += n

    def _aceptar(self):
        while True:
            try:
                cliente, _ = self._escucha.accept()
            except OSError:
                return
            threading.Thread(target=self._puentear, args=(cliente,), daemon=True).start()

    def _puentear(self, cliente: socket.socket):
        try:
            servidor = socket.create_connection(self.destino)
        except OSError:
            cliente.close()
            return
        cliente.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        servidor.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        threading.Thread(target=self._copiar, args=(cliente, servidor, True), daemon=True).start()
        threading.Thread(target=self._copiar, args=(servidor, cliente, False), daemon=True).start()

    def _copiar(self, origen, destino, es_subida):
        try:
            while True:
                datos = origen.recv(65536)
                if not datos:
                    break
                self._sumar(es_subida, len(datos))
                destino.sendall(datos)
        except OSError:
            pass
        finally:
            for s in (origen, destino):
                try:
                    s.shutdown(socket.SHUT_WR)
                except OSError:
                    pass


def rest_por_libreria(proxy, id_articulo, reusar=None):
    sesion = reusar or requests.Session()
    r = sesion.get(f"http://{proxy.destino_local}/articulos/{id_articulo}", timeout=5)
    return sesion, r


def rest_a_mano(proxy, id_articulo):
    s = socket.create_connection(("127.0.0.1", proxy.puerto), timeout=5)
    peticion = (
        f"GET /articulos/{id_articulo} HTTP/1.1\r\n"
        f"Host: {HOST}\r\n"
        f"\r\n"
    ).encode()
    s.sendall(peticion)
    time.sleep(0.4)
    s.settimeout(1.0)
    respuesta = b""
    try:
        while True:
            b = s.recv(65536)
            if not b:
                break
            respuesta += b
    except socket.timeout:
        pass
    s.close()
    return len(peticion), len(respuesta)


def grpc_llamada(stub, id_articulo):
    try:
        return stub.Consultar(catalogo_pb2.PeticionArticulo(id=id_articulo), timeout=5)
    except grpc.RpcError:
        return None


def fila(nombre, sub, baj):
    print(f"  {nombre:<46} {sub:>7} {baj:>9} {sub + baj:>8}")


def main():
    proxy_rest = ProxyMedidor(HOST, PUERTO_REST)
    proxy_grpc = ProxyMedidor(HOST, PUERTO_GRPC)

    print()
    print("=" * 78)
    print("MEDICION DE BYTES EN LA MISMA LLAMADA  (consultar articulo por ID)")
    print("=" * 78)
    print("  Metodo : un proxy TCP contador escrito en este mismo archivo se interpone")
    print("           entre el cliente y el servidor, reenvia todo tal cual y suma")
    print("           len() de cada bloque que pasa en cada direccion.")
    print("  Unidad : bytes de payload TCP. NO incluye cabeceras TCP/IP/Ethernet,")
    print("           que son identicas para los dos protocolos.")
    print(f"  ID usado : {ID_PRUEBA!r}   |   ID inexistente: {ID_FALLO!r}")
    print()
    print(f"  {'escenario':<46} {'subida':>7} {'bajada':>9} {'total':>8}")
    print("  " + "-" * 72)

    proxy_rest.reiniciar()
    sesion, r = rest_por_libreria(proxy_rest, ID_PRUEBA)
    sub, baj = proxy_rest.leer()
    fila("REST  HTTP/1.1 + JSON, conexion nueva", sub, baj)
    cuerpo_json = r.content

    proxy_rest.reiniciar()
    rest_por_libreria(proxy_rest, ID_PRUEBA, reusar=sesion)
    sub_rest_cal, baj_rest_cal = proxy_rest.leer()
    fila("REST  HTTP/1.1 + JSON, conexion reusada", sub_rest_cal, baj_rest_cal)

    proxy_rest.reiniciar()
    sub_min, baj_min = rest_a_mano(proxy_rest, ID_PRUEBA)
    fila("REST  HTTP/1.1 minimo escrito a mano", sub_min, baj_min)

    proxy_rest.reiniciar()
    rest_por_libreria(proxy_rest, ID_FALLO, reusar=sesion)
    sub, baj = proxy_rest.leer()
    fila("REST  ID inexistente (404), conexion reusada", sub, baj)
    sesion.close()

    print("  " + "-" * 72)

    proxy_grpc.reiniciar()
    canal = grpc.insecure_channel(proxy_grpc.destino_local)
    stub = catalogo_pb2_grpc.CatalogoStub(canal)
    articulo = grpc_llamada(stub, ID_PRUEBA)
    sub, baj = proxy_grpc.leer()
    fila("gRPC  HTTP/2 + protobuf, canal nuevo", sub, baj)

    proxy_grpc.reiniciar()
    grpc_llamada(stub, ID_PRUEBA)
    sub_grpc_cal, baj_grpc_cal = proxy_grpc.leer()
    fila("gRPC  HTTP/2 + protobuf, canal reusado", sub_grpc_cal, baj_grpc_cal)

    proxy_grpc.reiniciar()
    grpc_llamada(stub, ID_FALLO)
    sub, baj = proxy_grpc.leer()
    fila("gRPC  ID inexistente (NOT_FOUND), canal reusado", sub, baj)
    canal.close()

    peticion_pb = catalogo_pb2.PeticionArticulo(id=ID_PRUEBA)
    print()
    print("  Solo la carga util, sin nada de protocolo:")
    print(f"    JSON de respuesta            : {len(cuerpo_json):>5} B   {json.loads(cuerpo_json)}")
    print(f"    protobuf Articulo serializado: {articulo.ByteSize():>5} B")
    print(f"    path REST de la peticion     : {len('/articulos/' + ID_PRUEBA):>5} B")
    print(f"    protobuf PeticionArticulo    : {peticion_pb.ByteSize():>5} B")

    total_rest = sub_rest_cal + baj_rest_cal
    total_grpc = sub_grpc_cal + baj_grpc_cal
    print()
    print(f"  En caliente: REST {total_rest} B vs gRPC {total_grpc} B "
          f"({total_rest / total_grpc:.2f}x)")
    print("=" * 78)
    print()


if __name__ == "__main__":
    main()
