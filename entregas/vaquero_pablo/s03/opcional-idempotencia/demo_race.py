"""Lanza dos peticiones HTTP a la vez contra el servicio ya iniciado."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import http.client
import json
import threading
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    key = "demo-" + str(uuid.uuid4())
    barrier = threading.Barrier(2)

    def send(number):
        conn = http.client.HTTPConnection("127.0.0.1", args.port, timeout=15)
        try:
            barrier.wait(timeout=5)
            conn.request("POST", "/charges", json.dumps({"amount": 10000, "currency": "MXN"}), {
                "Content-Type": "application/json", "Idempotency-Key": key,
            })
            response = conn.getresponse()
            return number, response.status, response.getheader("Idempotency-Replayed"), response.read()
        finally:
            conn.close()

    print(f"Llave compartida: {key}")
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(send, [1, 2]))
    for number, status, replay, body in results:
        print(f"Petición {number}: HTTP {status}, Idempotency-Replayed: {replay}\n{body.decode()}")
    if sorted(r[2] or "" for r in results) != ["false", "true"] or any(r[1] != 201 for r in results):
        raise SystemExit("Resultado inesperado; revisa los errores del servidor")
    if results[0][3] != results[1][3]:
        raise SystemExit("ERROR: las respuestas no coinciden")
    print("OK: una creación y una repetición con el mismo ID y cuerpo.")


if __name__ == "__main__":
    main()
