"""Genera el reporte S05 a partir de las respuestas guardadas por ejecutar_lab.py."""
import json
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Preformatted, Table, TableStyle, Image

HERE = Path(__file__).parent
FONT_DIR = Path("C:/Windows/Fonts")
pdfmetrics.registerFont(TTFont("Arial", str(FONT_DIR / "arial.ttf")))
pdfmetrics.registerFont(TTFont("Arial-Bold", str(FONT_DIR / "arialbd.ttf")))
pdfmetrics.registerFont(TTFont("Arial-Italic", str(FONT_DIR / "ariali.ttf")))
pdfmetrics.registerFont(TTFont("Arial-BoldItalic", str(FONT_DIR / "arialbi.ttf")))
pdfmetrics.registerFontFamily("Arial", normal="Arial", bold="Arial-Bold",
                              italic="Arial-Italic", boldItalic="Arial-BoldItalic")
data = json.loads((HERE / "resultados.json").read_text(encoding="utf-8"))
doc = SimpleDocTemplate(str(HERE / "reporte-s05.pdf"), pagesize=(21 * cm, 29.7 * cm),
                        leftMargin=2 * cm, rightMargin=2 * cm, topMargin=1.7 * cm, bottomMargin=1.5 * cm)
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleS05", parent=styles["Title"], fontName="Arial-Bold", fontSize=17, alignment=TA_CENTER, spaceAfter=10))
styles.add(ParagraphStyle(name="BodyS05", parent=styles["BodyText"], fontName="Arial", fontSize=9.5, leading=13, spaceAfter=7))
styles.add(ParagraphStyle(name="HeadS05", parent=styles["Heading2"], fontName="Arial-Bold", fontSize=11.5, spaceBefore=12, spaceAfter=6))
styles.add(ParagraphStyle(name="CodeS05", fontName="Arial", fontSize=7.4, leading=9, backColor=colors.HexColor("#f2f4f6"), borderPadding=7, spaceAfter=8))
story = []


def p(text):
    story.append(Paragraph(text, styles["BodyS05"]))


def h(text):
    story.append(Paragraph(text, styles["HeadS05"]))


def code(text):
    story.append(Preformatted(text, styles["CodeS05"], maxLineLength=90, splitChars=" ,"))


def screenshot(filename, caption):
    path = HERE / "capturas" / filename
    image = Image(str(path), width=17 * cm, height=17 * cm * image_ratio(path))
    story.append(image)
    story.append(Paragraph(caption, ParagraphStyle(name="Caption" + filename,
                           parent=styles["BodyS05"], fontName="Arial-Italic",
                           fontSize=8, alignment=TA_CENTER, textColor=colors.HexColor("#4b5563"))))


def image_ratio(path):
    from PIL import Image as PILImage
    with PILImage.open(path) as source:
        return source.height / source.width


def entry(name, last=False):
    matches = [x["resultado"] for x in data if x["paso"] == name]
    return matches[-1] if last else matches[0]


def value(name, last=False):
    return entry(name, last)["respuesta"]


story.append(Paragraph("Laboratorio 5 · Mexi Banco con Docker Compose", styles["TitleS05"]))
p("<b>Alumna:</b> Amy Veraza &nbsp;&nbsp; <b>Versión:</b> v05.1 &nbsp;&nbsp; <b>Fecha:</b> 21 de septiembre de 2026")
p("<b>Fuente de evidencia:</b> ejecución automatizada de los mismos endpoints de la colección de Postman; "
  "las respuestas completas y los estados de Compose quedaron en <font name='Arial'>resultados.json</font>. "
  "El puerto 8080 del equipo estaba ocupado, por lo que publiqué el 8080 del contenedor en localhost:8081.")

h("1. Clonación y arranque")
p("Cloné la etiqueta v05.1 del repositorio oficial. Al construir, el archivo <font name='Arial'>mvnw</font> tenía "
  "saltos CRLF, que Alpine no podía ejecutar; cambié a LF solo en la copia local. La imagen se construyó "
  "en dos etapas y Compose levantó PostgreSQL y la aplicación. La base llegó a healthy antes de arrancar la aplicación.")
healthy = next(x["resultado"]["salida"] for x in data if x["paso"] == "compose" and
               x["resultado"].get("comando") == "docker compose ps" and
               "app" in x["resultado"]["salida"] and x["resultado"]["salida"].count("healthy") == 2)
code("$ docker compose ps\n" + healthy.replace("â€¦", "...").strip())
p("El endpoint <font name='Arial'>GET /actuator/health</font> devolvió HTTP 200 y estado UP.")
screenshot("01-compose-healthy.png", "Captura 1. Compose muestra app y db en estado healthy.")

h("2. Cuentas y transferencia interna")
p("Abrí la cuenta origen con 1000 y la destino con 500. Una transferencia de 200 dejó 800 en origen y "
  "700 en destino. La suma permaneció en 1500: el cargo y el abono cuadran.")
code("POST /transferencias → HTTP " + str(entry("transferencia")["status"]) + "\n" +
     json.dumps(value("transferencia"), ensure_ascii=False, indent=2) +
     "\nGET origen → saldo 800\nGET destino → saldo 700")
screenshot("02-transferencia.png", "Captura 2. Transferencia de 200 y comprobación de ambos saldos.")

h("3. SPEI e idempotencia")
p("Envié dos veces el mismo SPEI de 50 con <font name='Arial'>Idempotency-Key: veraza-amy-s05-001</font>. "
  "Ambas respuestas refieren al mismo identificador y estado ENVIADO; el saldo final fue 750. "
  "El estado de cuenta registró un solo movimiento SPEI_ENVIADO de -50.")
code("Primer envío: HTTP 201\n" + json.dumps(value("SPEI primero"), ensure_ascii=False, indent=2) +
     "\nReintento: HTTP 201\n" + json.dumps(value("SPEI reintento"), ensure_ascii=False, indent=2))
screenshot("03-spei-idempotencia.png", "Captura 3. Los dos envíos regresan el mismo SPEI y el saldo queda en 750.")
p("La tabla de solicitudes SPEI impone unicidad sobre la clave. El servicio reserva la clave antes de cobrar; "
  "si ya existe una solicitud terminada, devuelve la misma fila sin ejecutar de nuevo el cargo. "
  "Las notificaciones y movimientos confirman un solo cobro.")

h("4. Parada de app y persistencia")
p("Detuve app con <font name='Arial'>docker compose stop app</font>. Tras 15 segundos no volvió sola y "
  "la solicitud HTTP falló. La base siguió healthy. Al iniciar app otra vez, la cuenta conservó saldo 750. "
  "También sobrevivió a <font name='Arial'>docker compose down</font> y "
  "<font name='Arial'>docker compose up -d</font>. Después de <font name='Arial'>docker compose down -v</font> "
  "y un nuevo arranque, consultar esa CLABE devolvió 404: se eliminó el volumen con la base.")
code("app detenida → conexión rechazada\n" +
     "stop/start → saldo 750\ndown/up → saldo 750\ndown -v/up → GET /cuentas/002180000000000001: HTTP " +
     str(entry("sin volumen")["status"]))
screenshot("04-persistencia.png", "Captura 4. Prueba de parada, persistencia y eliminación del volumen.")

h("5. Código y configuración")
p("El monolito tiene módulos cuenta, movimiento, transferencia, notificación y SPEI. Cada módulo separa "
  "controlador HTTP, servicio con reglas de negocio, repositorio de datos y entidad. "
  "El archivo Compose define app y db en la red mexibanco; app resuelve db por nombre y espera su healthcheck. "
  "El volumen nombrado mexibanco_datos guarda PostgreSQL. El Dockerfile usa JDK 21 para compilar y JRE 21 "
  "para ejecutar el JAR, reduciendo la imagen de ejecución.")

h("6. Errores y conclusiones")
rows = [["Caso", "HTTP"]] + [[name, str(entry(name)["status"])] for name in
    ["CLABE inexistente", "CLABE repetida", "sin CLABE", "saldo insuficiente", "misma cuenta", "SPEI sin clave"]]
table = Table(rows, colWidths=[10 * cm, 2.5 * cm], hAlign="LEFT")
table.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#dce6ef")),
                           ("GRID", (0, 0), (-1, -1), 0.3, colors.grey),
                           ("FONTNAME", (0, 0), (-1, 0), "Arial-Bold"),
                           ("FONTNAME", (0, 1), (-1, -1), "Arial"),
                           ("FONTSIZE", (0, 0), (-1, -1), 8.5), ("BOTTOMPADDING", (0, 0), (-1, -1), 5)]))
story.append(table)
story.append(Spacer(1, 6))
p("Después de estos errores, el saldo de origen permaneció en 1000 en la base nueva.")
p("<b>¿Qué aportó Compose?</b> Levantó y conectó los dos servicios, esperó la salud de db y conservó los "
  "datos mediante el volumen. Me sorprendió que detener app manualmente no la reinició.")
p("<b>¿Qué resolvería restart: always?</b> Reiniciaría el contenedor en este mismo host ante una caída "
  "o parada no intencional. No reubica la carga si falla la máquina, ni proporciona réplicas, balanceo, "
  "descubrimiento o actualizaciones coordinadas. Además, una parada manual puede quedar respetada "
  "hasta que se reinicie el daemon o el contenedor.")
p("<b>¿Qué falta al escalar app?</b> Cada réplica necesita un puerto distinto del host; sin balanceador, "
  "las peticiones al 8080 llegan solo a una. Un Service de Kubernetes ofrece una dirección estable y "
  "distribuye tráfico entre réplicas sanas.")


def footer(canvas, document):
    canvas.setFont("Arial", 8)
    canvas.setFillColor(colors.grey)
    canvas.drawString(2 * cm, 1 * cm, "SD 2027-1 · Laboratorio S05 · Amy Veraza")
    canvas.drawRightString(19 * cm, 1 * cm, str(document.page))


doc.build(story, onFirstPage=footer, onLaterPages=footer)
print(HERE / "reporte-s05.pdf")
