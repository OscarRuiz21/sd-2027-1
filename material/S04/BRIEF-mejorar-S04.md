# Brief para mejorar el deck de la S04 · Sistemas Distribuidos 2027-1 (FI-UNAM, grupo 2)

Pegar este archivo (o su ruta) al abrir la conversación nueva.
Ruta: `~/Documents/Repos/sd-2027-1/material/S04/BRIEF-mejorar-S04.md`

## 1. Qué se quiere

1. **Extender la sección B, "Cómo se hablan"** (hoy slides 4 y 5) con el material nuevo de gRPC:
   dos reels ya transcritos y un reel de "gRPC vs REST" en fotogramas.
2. **Profundizar REST** antes de gRPC: hoy REST solo aparece como recordatorio de la S3 (slide 3)
   y como columna de comparación (slide 5).
3. **Rehacer la sección C, "Las seis piezas"** (slides 7–14) con el skill de diagramas animados,
   para que cada pieza se construya paso a paso en lugar de aparecer completa.

## 2. Estado: la clase de mañana ya está cerrada

Sábado 12 de septiembre de 2026, 07:00–11:00. Todo está hecho, verificado y publicado en el repo
`~/Documents/Repos/sd-2027-1`:

| Pieza | Estado |
|---|---|
| Deck `S04-Clase-4-SD` (21 slides) | pptx + pdf en `material/S04/` |
| Notas de clase (una página por slide) | docx + pdf, solo local |
| `labs/Lab-S04-Docker-dia-2.html` | publicado, 48 checks corriendo y pasando |
| Repaso de la S03 para pase de lista | 10 preguntas con respuesta y pista, + 3 de reserva |
| Mensaje de la semana | publicado el domingo 6 |
| Lectura 3 (12-factor + Borg) | Discussion #25, cierra mañana 06:59 |
| `02-spec-S04.md` | plan minuto a minuto y regla de válvulas (no está en el repo; pedirlo si hace falta) |

**Cómo corre el sábado:** 07:00–08:00 guía del lab en pantalla hasta compose · 08:00–10:00 teoría,
21 slides · 10:00–11:00 reto de las cuatro misiones, entrega por push antes del domingo.

**El arco:** gRPC como continuación de REST (4 y 5) → la pregunta de la semana sobre el factor III (6)
→ las seis piezas con 12-factor colgado de ellas (7–14) → los datos (15 y 16) → FarmaYa como la
decisión de hoy a las 09:44, que la S3 dejó pendiente y que nunca se recorta.

**Válvulas, en orden:** slide 5 a dos minutos con la pura tabla si a las 08:16 va tarde · slide 14 a
una frase · slide 16 a treinta segundos.

Por eso: **cualquier cambio se piensa para una versión futura del deck, no para mañana**, salvo que
el profesor diga lo contrario, y debe respetar el presupuesto de tiempo y las válvulas.

## 3. El deck actual, slide por slide

1. Portada · Anatomía de un sistema real, segunda parte
2. El mapa de la sesión: 120 minutos, una decisión al final
3. Lo que quedó de la S3 en cuatro frases: timeout, exactly-once, REST, idempotencia
4. **gRPC: RPC vuelve, con contrato y con deadline** (.proto, protoc, stub, 38 → 9 bytes, 26-feb-2015)
5. **REST o gRPC: mismo problema, trade-offs distintos** (tabla de 6 filas; válvula)
6. Pregunta de la semana: ¿dónde vive la configuración? (factor III)
7. Partir el monolito crea cinco problemas nuevos y uno de visibilidad (el mapa del sistema)
8. API Gateway · 9. Service discovery · 10. Config server · 11. Broker · 12. Caché · 13. Observabilidad
   (las seis con el mismo mapa arriba y tres columnas: problema, dato duro, incidente real)
14. 12-factor: cuatro factores que ya vieron en las piezas (III, VI, IV, XI; válvula)
15. Cada servicio es dueño de su almacén (polyglot persistence)
16. Seis familias, seis preguntas (relacional, documental, llave-valor, columnar, grafo, objetos; válvula)
17. El mapa completo: seis piezas, un problema cada una
18. La decisión de hoy · FarmaYa: ¿reintentar o no? (A, B, C con requisito y sacrificio)
19. Para la S5: Raft y compose
20. Ocho preguntas que hoy ya pueden responder
21. Fuentes

Estilo del deck: español, una idea por slide, datos con fecha y versión, un incidente real por pieza,
y el sistema de referencia (Mexi Banco: gateway, cuentas, transferencias, notificaciones, PostgreSQL,
MongoDB, Redis, RabbitMQ, Eureka, config server, observabilidad).

> Ojo: en el repo **no hay script que genere el pptx**. Si el deck se construyó con python-pptx desde
> otra conversación o carpeta, pedir esa ruta antes de intentar regenerarlo.

## 4. Material nuevo, ya procesado

Todo en `material/S04/videos/`:

| Archivo | Qué es |
|---|---|
| `ScreenRecording_09-11-2026 19-32-15_1.mov` (4:34) | dos reels seguidos de gRPC |
| `grpc_frames/` (275 jpg, 1 fps) + `grpc_contactsheet.jpg` | fotogramas del anterior |
| `ScreenRecording_09-11-2026 19-39-48_1.mov` (12 s) | "12 API Concepts" + reel "gRPC vs REST" |
| `api12_frames/` (23 jpg, 2 fps) + `api12_contactsheet.jpg` | fotogramas del anterior |
| `transcripts/grpc-2026-09-11-1932.md` | transcripción Plaud del video largo, ya limpia y ordenada |
| `transcripts/grpc-vs-rest-frames.md` | contenido del reel de payloads, leído de los fotogramas |

Lo que aportan y que hoy **no** está en el deck:

- **Por qué HTTP/2 antes que gRPC:** una acción del usuario se vuelve N llamadas entre servicios;
  muchas conexiones cuestan handshakes y encolan peticiones; HTTP/2 da una conexión con varios
  streams y compresión de encabezados. El deck salta directo a "HTTP/2" como fila de la tabla.
- **Los cuatro modos de llamada:** unario, streaming de servidor, de cliente y bidireccional.
- **Por qué el binario pesa menos, con números:** REST carga el nombre de cada campo en cada mensaje
  (180 B); el contrato numerado del `.proto` permite que gRPC mande solo número y valor (60 B).
- **Caso concreto tipo Uber:** viaje → conductores cercanos, precio, ubicación, notificación.
- **gRPC no reemplaza HTTP** y el navegador no habla gRPC nativo (gRPC-Web como puente).
- **Mapa de 12 conceptos de API** (REST, idempotencia, paginación, rate limits, versionado, webhooks,
  gRPC, GraphQL, auth, reintentos, timeouts, códigos de estado) como banco de temas para REST.

## 5. El skill de diagramas animados

Instalado a nivel usuario, disponible en cualquier conversación: `~/.claude/skills/diagramas-animados/`.
Se invoca pidiéndolo en palabras ("hazme un diagrama animado de X", "usa el skill diagramas-animados").

Qué hace: genera **un HTML autocontenido** (escenario 1600×900 que escala a cualquier pantalla) que
avanza por escenas y pasos. Cada paso revela o cambia algo del diagrama y muestra una línea del guion
como subtítulo. Produce también `guion.md`, con el guion numerado y notas para quien presenta.

- `scripts/deck.py init <carpeta> --title "…" --brand "…"` · `build <carpeta>` · `shots <carpeta> 3.2 5.4`
- `references/dsl.md`: formato de escenas (nodos, chips, listas, tablas, conexiones con flechas y
  etiquetas, diagramas de secuencia, temas por escena, tipos propios).
- Ejemplos: `assets/examples/` (11 escenas de sistemas distribuidos) y `assets/examples/pbs-fl/`
  (tema claro institucional + tipos a la medida).
- Controles al presentar: → avanza, ← regresa, **N** guion y notas, **P** automático, **F** pantalla completa.

Decks ya hechos con él: `~/claude-general/sistemas_distribuidos/` (clase de Sistemas Distribuidos) y
`~/Documents/Repos/ruiz-master-tesis/cic_expo/ppt_seminario/animada/` (seminario PBS-FL).

## 6. Trabajo propuesto

1. **REST, más a fondo** (una o dos slides antes de gRPC): recursos y verbos, códigos de estado,
   idempotencia ya vista en la S3, paginación, versionado, rate limits, y por qué el contrato es
   opcional. Fuente: la retícula de 12 conceptos y RFC 9110.
2. **gRPC extendido** (dos o tres slides en lugar de una): (a) por qué HTTP/2, con el caso de la app
   de viajes; (b) el `.proto` como contrato y el código generado; (c) los cuatro modos de llamada;
   (d) el ahorro en bytes con la comparación 180 B contra 60 B.
3. **Sección C animada con el skill:** una escena por pieza, cada una construyendo el mapa del sistema
   paso a paso (aparece el problema, aparece la pieza, aparece el dato duro, aparece el incidente),
   más una escena de recapitulación con las seis juntas. Sirve como complemento proyectable o como
   base para rehacer esas slides del pptx.

Al terminar, decidir con el profesor si el resultado es: (a) slides nuevas para el pptx, (b) un HTML
animado que acompaña al pptx, o (c) las dos cosas.

## 7. Cómo arrancar la conversación nueva

> Lee `~/Documents/Repos/sd-2027-1/material/S04/BRIEF-mejorar-S04.md` y, con el skill
> `diagramas-animados`, ayúdame a extender la sección de gRPC y REST de la S04 y a rehacer la
> sección de las seis piezas. Los fotogramas y las transcripciones ya están en `material/S04/videos/`.

## 8. Ya hecho: complemento animado

`material/S04/animada/` es un deck animado de 12 escenas y 58 pasos hecho con el skill:
REST a fondo (00–01), el puente de una acción a N llamadas (02), HTTP/2 (03), protobuf con 180 B → 60 B (04),
el .proto y el código generado (05), los cuatro modos (06), el trade-off REST/gRPC (07) y las seis piezas (08–11).
Se reconstruye con `python3 ~/.claude/skills/diagramas-animados/scripts/deck.py build material/S04/animada`.
El guion numerado, con notas para presentar, está en `material/S04/animada/guion.md`.
