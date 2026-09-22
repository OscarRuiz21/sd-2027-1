"""Ejecuta las comprobaciones del laboratorio S05 y guarda resultados reales."""
import json
import http.client
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4] / "mexi-banco"
OUT = Path(__file__).with_name("resultados.json")
BASE = "http://localhost:8081"
ORIGEN = "002180000000000001"
DESTINO = "012180000000000001"
KEY = "veraza-amy-s05-001"
results = []


def record(name, value):
    results.append({"paso": name, "resultado": value})
    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")


def compose(*args):
    proc = subprocess.run(["docker", "compose", *args], cwd=ROOT,
                          capture_output=True, text=True, encoding="utf-8")
    value = {"comando": "docker compose " + " ".join(args),
             "codigo": proc.returncode, "salida": proc.stdout + proc.stderr}
    record("compose", value)
    if proc.returncode:
        raise RuntimeError(value)
    return proc.stdout


def request(name, method, path, body=None, headers=None):
    payload = json.dumps(body).encode() if body is not None else None
    hdrs = {"Content-Type": "application/json"} if body is not None else {}
    hdrs.update(headers or {})
    req = urllib.request.Request(BASE + path, data=payload, method=method, headers=hdrs)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            status, raw = response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read().decode()
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        parsed = raw
    value = {"metodo": method, "ruta": path, "status": status, "respuesta": parsed}
    record(name, value)
    return value


def wait_healthy():
    for _ in range(60):
        try:
            if request("salud", "GET", "/actuator/health")["status"] == 200:
                return
        except (OSError, http.client.HTTPException):
            pass
        time.sleep(2)
    raise RuntimeError("La aplicación no llegó a healthy")


def saldo(clabe):
    return request("cuenta " + clabe, "GET", "/cuentas/" + clabe)["respuesta"]["saldo"]


def run():
    compose("ps")
    wait_healthy()
    request("abrir origen", "POST", "/cuentas", {"clabe": ORIGEN, "titular": "Amy Veraza", "saldoInicial": 1000})
    request("abrir destino", "POST", "/cuentas", {"clabe": DESTINO, "titular": "Destino S05", "saldoInicial": 500})
    assert str(saldo(ORIGEN)).split(".")[0] == "1000"
    transfer = request("transferencia", "POST", "/transferencias", {"claveOrigen": ORIGEN, "claveDestino": DESTINO, "monto": 200})
    assert transfer["status"] in (200, 201)
    assert str(saldo(ORIGEN)).split(".")[0] == "800"
    assert str(saldo(DESTINO)).split(".")[0] == "700"
    body = {"claveOrigen": ORIGEN, "bancoDestino": "BBVA", "claveDestino": "012180000000000099", "monto": 50}
    first = request("SPEI primero", "POST", "/spei", body, {"Idempotency-Key": KEY})
    second = request("SPEI reintento", "POST", "/spei", body, {"Idempotency-Key": KEY})
    assert first["respuesta"] == second["respuesta"]
    assert str(saldo(ORIGEN)).split(".")[0] == "750"
    request("movimientos", "GET", "/movimientos?clabe=" + ORIGEN)
    request("notificaciones", "GET", "/notificaciones?clabe=" + ORIGEN)
    compose("stop", "app")
    time.sleep(15)
    compose("ps")
    try:
        request("app detenida", "GET", "/actuator/health")
        raise AssertionError("La aplicación respondió estando detenida")
    except urllib.error.URLError:
        record("app detenida", "La conexión falló, como se esperaba")
    compose("start", "app")
    resume()


def resume():
    wait_healthy()
    compose("ps")
    assert str(saldo(ORIGEN)).split(".")[0] == "750"
    compose("down")
    compose("up", "-d")
    wait_healthy()
    assert str(saldo(ORIGEN)).split(".")[0] == "750"
    compose("down", "-v")
    compose("up", "-d")
    wait_healthy()
    assert request("sin volumen", "GET", "/cuentas/" + ORIGEN)["status"] == 404
    compose("ps")
    record("conclusion", "Todas las comprobaciones pasaron")


def errors():
    request("abrir origen tras borrar volumen", "POST", "/cuentas", {"clabe": ORIGEN, "titular": "Amy Veraza", "saldoInicial": 1000})
    request("abrir destino tras borrar volumen", "POST", "/cuentas", {"clabe": DESTINO, "titular": "Destino S05", "saldoInicial": 500})
    cases = [
        ("CLABE inexistente", "GET", "/cuentas/999999999999999999", None, None, 404),
        ("CLABE repetida", "POST", "/cuentas", {"clabe": ORIGEN, "titular": "Otra", "saldoInicial": 1}, None, 409),
        ("sin CLABE", "POST", "/cuentas", {"titular": "Sin clabe", "saldoInicial": 1}, None, 400),
        ("saldo insuficiente", "POST", "/transferencias", {"claveOrigen": ORIGEN, "claveDestino": DESTINO, "monto": 999999}, None, 422),
        ("misma cuenta", "POST", "/transferencias", {"claveOrigen": ORIGEN, "claveDestino": ORIGEN, "monto": 1}, None, 422),
        ("SPEI sin clave", "POST", "/spei", {"claveOrigen": ORIGEN, "bancoDestino": "BBVA", "claveDestino": "012180000000000099", "monto": 1}, None, 400),
    ]
    for name, method, path, body, headers, expected in cases:
        assert request(name, method, path, body, headers)["status"] == expected
    assert str(saldo(ORIGEN)).split(".")[0] == "1000"
    record("errores", "Códigos esperados y saldo sin cambios")


if __name__ == "__main__":
    try:
        if "--resume" in sys.argv:
            results.extend(json.loads(OUT.read_text(encoding="utf-8")))
            resume()
        elif "--errors" in sys.argv:
            results.extend(json.loads(OUT.read_text(encoding="utf-8")))
            errors()
        else:
            run()
    except Exception as exc:
        record("error", repr(exc))
        raise
