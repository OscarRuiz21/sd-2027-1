import os
import sys
import time

import grpc
import requests

import catalogo_pb2
import catalogo_pb2_grpc

HOST = os.getenv("SERVIDOR_HOST", "servidor")
PUERTO_REST = os.getenv("PUERTO_REST", "8000")
PUERTO_GRPC = os.getenv("PUERTO_GRPC", "50051")

BASE_REST = f"http://{HOST}:{PUERTO_REST}"
DESTINO_GRPC = f"{HOST}:{PUERTO_GRPC}"

IDS = os.getenv("IDS", "A-100,B-200,a-100 ,Z-999").split(",")


def esperar_servidor(intentos: int = 40, espera: float = 0.5) -> None:
    for _ in range(intentos):
        try:
            if requests.get(f"{BASE_REST}/salud", timeout=2).status_code == 200:
                print(f"[cliente] servidor listo en {HOST}", flush=True)
                return
        except requests.RequestException:
            pass
        time.sleep(espera)
    print(f"[cliente] el servidor no respondio en {HOST}", file=sys.stderr)
    sys.exit(1)


def llamar_rest(sesion: requests.Session, id_articulo: str) -> tuple[dict, str]:
    r = sesion.get(f"{BASE_REST}/articulos/{id_articulo}", timeout=5)
    if r.status_code == 404:
        return {"encontrado": False, "mensaje": r.json()["error"]}, "HTTP 404"
    r.raise_for_status()
    return {"encontrado": True, **r.json()}, "HTTP 200"


def llamar_grpc(stub, id_articulo: str) -> tuple[dict, str]:
    try:
        a = stub.Consultar(catalogo_pb2.PeticionArticulo(id=id_articulo), timeout=5)
    except grpc.RpcError as e:
        if e.code() == grpc.StatusCode.NOT_FOUND:
            return {"encontrado": False, "mensaje": e.details()}, "status NOT_FOUND"
        raise
    return {
        "encontrado": True,
        "id": a.id,
        "nombre": a.nombre,
        "categoria": a.categoria,
        "precio": a.precio,
        "existencias": a.existencias,
    }, "status OK"


def describir(resultado: dict) -> str:
    if not resultado["encontrado"]:
        return f"sin datos -> {resultado['mensaje']}"
    return (f"{resultado['nombre']} | {resultado['categoria']} | "
            f"${resultado['precio']:.2f} | stock {resultado['existencias']}")


def main() -> int:
    esperar_servidor()

    sesion = requests.Session()
    canal = grpc.insecure_channel(DESTINO_GRPC)
    stub = catalogo_pb2_grpc.CatalogoStub(canal)

    print()
    print(f"[cliente] IDs segun REST: {sesion.get(BASE_REST + '/articulos').json()['ids']}", flush=True)
    print(f"[cliente] IDs segun gRPC: {list(stub.Listar(catalogo_pb2.PeticionVacia()).ids)}", flush=True)

    iguales = 0
    for id_articulo in IDS:
        r, codigo_rest = llamar_rest(sesion, id_articulo)
        g, codigo_grpc = llamar_grpc(stub, id_articulo)
        coincide = "SI" if r == g else "NO"
        iguales += coincide == "SI"
        print()
        print(f"--- ID solicitado: {id_articulo!r}")
        print(f"    REST [{codigo_rest:<15}] : {describir(r)}")
        print(f"    gRPC [{codigo_grpc:<15}] : {describir(g)}")
        print(f"    mismo resultado de negocio: {coincide}")

    print()
    print(f"[cliente] {iguales}/{len(IDS)} consultas coincidieron entre las dos interfaces", flush=True)

    canal.close()
    return 0 if iguales == len(IDS) else 1


if __name__ == "__main__":
    sys.exit(main())
