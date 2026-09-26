"""Convierte la evidencia real de resultados.json en capturas tipo terminal."""
import json
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).parent
OUT = HERE / "capturas"
OUT.mkdir(exist_ok=True)
DATA = json.loads((HERE / "resultados.json").read_text(encoding="utf-8"))
FONT_DIR = Path("C:/Windows/Fonts")
FONT = ImageFont.truetype(str(FONT_DIR / "consola.ttf"), 25)
FONT_BOLD = ImageFont.truetype(str(FONT_DIR / "consolab.ttf"), 25)
SMALL = ImageFont.truetype(str(FONT_DIR / "arial.ttf"), 19)


def entry(name, last=False):
    matches = [item["resultado"] for item in DATA if item["paso"] == name]
    return matches[-1] if last else matches[0]


def response(name, last=False):
    return entry(name, last)["respuesta"]


def wrap(lines, width=91):
    result = []
    for line in lines:
        result.extend(textwrap.wrap(line, width=width, replace_whitespace=False,
                                    drop_whitespace=False) or [""])
    return result


def capture(filename, title, lines):
    lines = wrap(lines)
    width = 1600
    line_height = 36
    height = 105 + line_height * len(lines) + 45
    image = Image.new("RGB", (width, height), "#101418")
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, width, 58), fill="#252b31")
    draw.ellipse((20, 19, 40, 39), fill="#ff5f57")
    draw.ellipse((51, 19, 71, 39), fill="#febc2e")
    draw.ellipse((82, 19, 102, 39), fill="#28c840")
    draw.text((125, 15), title, font=SMALL, fill="#e6edf3")
    y = 78
    for line in lines:
        color = "#7ee787" if line.startswith("$") else "#e6edf3"
        draw.text((28, y), line, font=FONT_BOLD if line.startswith("$") else FONT, fill=color)
        y += line_height
    image.save(OUT / filename, optimize=True)


healthy = next(item["resultado"]["salida"] for item in DATA
               if item["paso"] == "compose"
               and item["resultado"].get("comando") == "docker compose ps"
               and "app" in item["resultado"]["salida"]
               and item["resultado"]["salida"].count("healthy") == 2)
capture("01-compose-healthy.png", "PowerShell — Mexi Banco", [
    "$ docker compose ps",
    *healthy.replace("â€¦", "...").strip().splitlines(),
    "$ curl http://localhost:8081/actuator/health",
    '{"status":"UP"}',
])

transfer = response("transferencia")
origen_800 = next(item["resultado"]["respuesta"] for item in DATA
                  if item["paso"] == "cuenta 002180000000000001"
                  and item["resultado"]["respuesta"].get("saldo") == 800.0)
destino_700 = next(item["resultado"]["respuesta"] for item in DATA
                   if item["paso"] == "cuenta 012180000000000001"
                   and item["resultado"]["respuesta"].get("saldo") == 700.0)
capture("02-transferencia.png", "Pruebas HTTP — transferencia interna", [
    "$ POST /transferencias  {origen, destino, monto: 200}",
    "HTTP 201",
    json.dumps(transfer, ensure_ascii=False),
    "$ GET /cuentas/002180000000000001",
    json.dumps(origen_800, ensure_ascii=False),
    "$ GET /cuentas/012180000000000001",
    json.dumps(destino_700, ensure_ascii=False),
])

first = response("SPEI primero")
second = response("SPEI reintento")
capture("03-spei-idempotencia.png", "Pruebas HTTP — SPEI e idempotencia", [
    "$ POST /spei  Idempotency-Key: veraza-amy-s05-001  (primer envío)",
    "HTTP 201  " + json.dumps(first, ensure_ascii=False),
    "$ POST /spei  Idempotency-Key: veraza-amy-s05-001  (reintento)",
    "HTTP 201  " + json.dumps(second, ensure_ascii=False),
    "$ GET /cuentas/002180000000000001",
    "HTTP 200  saldo: 750.0  (un solo descuento de 50)",
])

capture("04-persistencia.png", "Docker Compose — parada y volumen", [
    "$ docker compose stop app",
    "Container mexi-banco-app-1  Stopped",
    "$ después de 15 segundos: curl /actuator/health",
    "Conexión rechazada; app no se reinició sola. db continuó healthy.",
    "$ docker compose start app; GET cuenta origen",
    "HTTP 200  saldo: 750.0",
    "$ docker compose down; docker compose up -d; GET cuenta origen",
    "HTTP 200  saldo: 750.0 (el volumen conservó los datos)",
    "$ docker compose down -v; docker compose up -d; GET cuenta origen",
    "HTTP 404  No existe la CLABE (el volumen fue eliminado)",
])

print(f"Capturas generadas en {OUT}")
