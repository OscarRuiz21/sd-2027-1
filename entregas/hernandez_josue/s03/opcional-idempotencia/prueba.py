from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
from http.client import HTTPConnection
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Barrier, Thread

from servicio import conectar, crear_servidor


def solicitar(puerto, inicio):
    with closing(HTTPConnection("127.0.0.1", puerto, timeout=10)) as conexion:
        conexion.connect()
        inicio.wait(timeout=5)
        conexion.request(
            "POST",
            "/cobros",
            body='{"monto_centavos":12500}',
            headers={"Content-Type": "application/json", "Idempotency-Key": "pedido-17"},
        )
        respuesta = conexion.getresponse()
        return respuesta.status, json.loads(respuesta.read())


def main():
    with TemporaryDirectory() as temp:
        ruta = Path(temp) / "prueba.sqlite3"
        with crear_servidor(ruta, puerto=0) as servidor:
            hilo = Thread(target=servidor.serve_forever)
            hilo.start()
            try:
                inicio = Barrier(2)
                with ThreadPoolExecutor(max_workers=2) as pool:
                    peticiones = [pool.submit(solicitar, servidor.server_port, inicio) for _ in range(2)]
                    respuestas = [peticion.result() for peticion in peticiones]
            finally:
                servidor.shutdown()
                hilo.join()

        print("Dos solicitudes con Idempotency-Key: pedido-17")
        for numero, (estado, cuerpo) in enumerate(respuestas, 1):
            print(f"Solicitud {numero} | HTTP {estado} | {json.dumps(cuerpo)}")

        with closing(conectar(ruta)) as db:
            solicitudes = db.execute("SELECT COUNT(*) FROM solicitudes").fetchone()[0]
            cobros = db.execute("SELECT COUNT(*) FROM cobros").fetchone()[0]

        assert sorted(estado for estado, _ in respuestas) == [200, 201], respuestas
        assert respuestas[0][1] == respuestas[1][1]
        assert respuestas[0][1] == {"cobro_id": 1, "monto_centavos": 12500}
        assert (solicitudes, cobros) == (1, 1)
        print(f"SQLite | solicitudes: {solicitudes} | cobros: {cobros}")


if __name__ == "__main__":
    main()
