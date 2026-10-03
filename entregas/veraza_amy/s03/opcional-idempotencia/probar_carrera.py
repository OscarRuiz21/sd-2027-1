"""Envía dos cobros simultáneos con la misma Idempotency-Key."""
import json
import os
import threading
import urllib.request

URL = os.getenv("BASE_URL", "http://localhost:8080") + "/cobros"
KEY = "amy-cobro-001"
BODY = json.dumps({"monto": 250.0, "concepto": "Inscripción"}).encode()
barrier = threading.Barrier(3)
results = []


def send(number: int) -> None:
    barrier.wait()
    request = urllib.request.Request(
        URL,
        data=BODY,
        method="POST",
        headers={"Content-Type": "application/json", "Idempotency-Key": KEY},
    )
    with urllib.request.urlopen(request) as response:
        results.append((number, response.status, json.loads(response.read())))


threads = [threading.Thread(target=send, args=(number,)) for number in (1, 2)]
for thread in threads:
    thread.start()
barrier.wait()
for thread in threads:
    thread.join()

for result in sorted(results):
    print(f"Petición {result[0]}: HTTP {result[1]} {json.dumps(result[2], ensure_ascii=False)}")

with urllib.request.urlopen(URL) as response:
    charges = json.loads(response.read())
print(f"Cobros guardados: {len(charges)}")
print(json.dumps(charges, ensure_ascii=False, indent=2))
assert len(charges) == 1, "La misma clave produjo más de un cobro"
