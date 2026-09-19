# T01 · El mismo servicio, por REST y por gRPC

**Pablo Vaquero · Sistemas Distribuidos · Python 3.12**

Un catálogo recibe un ID y devuelve un producto. Los datos están en un diccionario
en memoria: ID 1 es un Cuaderno ($45.00 MXN) e ID 2 es un Lápiz ($10.00 MXN).
Los precios se representan en centavos enteros. Un ID positivo que no está en el
catálogo produce un error de «no hay datos».

## Ejecutar con Docker

Requisitos: Docker Desktop iniciado y Docker Compose v2. Desde esta carpeta:

```bash
docker compose up --build --abort-on-container-exit --exit-code-from client
```

Construye las dos imágenes, genera el código del `.proto`, crea la red `catalog`
y arranca el servidor. El cliente espera a que el chequeo de salud confirme que
REST y gRPC responden; después consulta los IDs **1, 2, 999 y 0** por ambos protocolos.
Compara tanto los datos como el significado de los estados. Cuando termina, Compose
detiene el servidor; un código de salida 0 indica que la demostración terminó bien.

Para dejar el servidor encendido y consultar manualmente:

```bash
docker compose up -d --build --wait server
docker compose build client
docker compose run --rm client --protocol both --ids 1 999
docker compose run --rm client --protocol rest --ids 2
docker compose run --rm client --protocol grpc --ids 2
curl -i http://localhost:18000/products/1
curl -i http://localhost:18000/products/999
docker compose down
```

Dentro de la red Docker, el cliente usa `http://server:8000` y `server:50051`.
`server` se resuelve mediante el DNS de Docker; `localhost` en el cliente apuntaría
al propio cliente. Desde la computadora, los puertos publicados son **18000** (REST)
y **15051** (gRPC), accesibles solo desde la propia computadora. Si están ocupados,
se puede cambiar el lado izquierdo de las asignaciones en `docker-compose.yml`.

## Dónde está la lógica compartida

```text
                        Un solo proceso de servidor
Cliente ── HTTP/JSON ──> RestController ─┐
                                       ├─> misma instancia de CatalogService
Cliente ── HTTP/2 ─────> GrpcController ─┘       └─> mismo diccionario
            Protobuf
```

`service.py` contiene la lógica: `CatalogService.get_product()` valida el ID,
busca en el diccionario y devuelve un `Product` o lanza una excepción de dominio.
Este archivo no importa HTTP ni gRPC. Es el único lugar donde se decide si un
producto existe y si el ID es válido.

`Server` en `server.py` crea **una sola instancia** de `CatalogService` y pasa esa
misma referencia a los dos controladores. Los hilos atienden ambos protocolos
dentro del mismo proceso y del mismo contenedor; el catálogo es de solo lectura.

Lo que cambia entre los controladores es la adaptación de entrada y salida:

| Aspecto | REST | gRPC |
| --- | --- | --- |
| Operación | `GET /products/{id}` | `catalog.Catalog/GetProduct` |
| Entrada | ID en la URL, convertido a entero | Campo `id` de tipo `int32` |
| Salida exitosa | JSON | Mensaje `Product` de Protobuf |
| Producto encontrado | HTTP `200` | `OK` |
| ID válido sin datos | HTTP `404` | `NOT_FOUND` |
| ID cero o negativo | HTTP `400` | `INVALID_ARGUMENT` |

El contrato admite IDs entre 1 y 2147483647. REST también rechaza texto y números
fuera de ese rango con `400`; Protobuf impide construir un `int32` fuera de rango
o con texto antes de enviar la llamada. El cliente limita sus argumentos a `int32`.
Los controladores traducen las mismas excepciones; no duplican el catálogo ni la
decisión de búsqueda. El cliente transforma las respuestas a una representación
comparable, pero no contiene una copia de los productos.

Ejemplo REST: `{"id":1,"name":"Cuaderno","price_cents":4500}`.
En gRPC son los mismos tres campos, codificados en formato binario. Para el ID 999,
ambos comunican `No hay datos para el ID 999`, con el estado propio de cada protocolo.

## El contrato y su generación

`proto/catalog.proto` define `GetProductRequest`, `Product` y el método `GetProduct`.
Los números 1, 2 y 3 de los campos son etiquetas de Protobuf, no valores de ejemplo.
No deben reutilizarse para un significado distinto al evolucionar el contrato.

Los dos Dockerfiles ejecutan:

```bash
python -m grpc_tools.protoc -Iproto --python_out=. --grpc_python_out=. proto/catalog.proto
```

Esto genera `catalog_pb2.py` (mensajes) y `catalog_pb2_grpc.py` (stub del cliente y
base del servidor). No se editan a mano ni se guardan en Git: se reconstruyen a
partir del `.proto` usando las versiones fijadas en `requirements.txt`.

## Pruebas y evidencia

```bash
docker compose build
docker compose run --rm --no-deps server python -m unittest -v
```

Las siete pruebas cubren productos existentes, ID inexistente, IDs inválidos,
texto en REST y la reutilización de la misma instancia. Las pruebas de transporte
abren sockets reales en puertos libres. Un servicio que registra sus llamadas
comprueba que REST y gRPC pasan por el mismo objeto.

Además, la demostración de Compose prueba **dos contenedores distintos** conectados
por la red Docker. Las salidas ejecutadas están en [EVIDENCIA.md](EVIDENCIA.md) y
en la carpeta `evidencia/`.

## Medición de bytes

```bash
docker compose up -d --build --wait server
docker compose build client
docker compose run --rm --no-deps --entrypoint python client measure.py
docker compose down
```

`measure.py` consulta el **mismo ID 1** por ambas interfaces y comprueba que los datos
coinciden. Cada consulta abre una conexión nueva sin TLS. Un intermediario TCP
local reenvía los bytes sin modificarlos y suma `len(data)` en ambas direcciones.
Solo observa las conexiones medidas, no las consultas del chequeo de salud.

Se reportan dos alcances distintos:

- **Cuerpo de respuesta:** bytes UTF-8 del JSON recibido (`len(raw)`) y tamaño del
  mensaje Protobuf recibido (`ByteSize()`). En Protobuf el cuerpo no incluye los
  5 bytes del prefijo gRPC ni los encabezados HTTP/2.
- **Flujo TCP de la conexión:** bytes reales reenviados desde la apertura hasta
  el cierre, incluyendo encabezados HTTP y, en gRPC, prefacio, tramas HTTP/2,
  encabezados, mensajes de control y envoltura gRPC que envíe esta implementación.
  Se excluyen cabeceras TCP/IP/Ethernet, ACK de TCP, retransmisiones y handshake TCP.

No es una captura de todos los bytes físicos de la red ni una prueba de rendimiento.
La comparación incluye el costo de abrir una conexión por consulta. Una conexión
gRPC reutilizada repartiría ese costo entre varias llamadas; tampoco se pretende
demostrar que un protocolo siempre transmite menos que el otro.

Resultados de la ejecución del 19 de septiembre de 2026:

| Medida en bytes | REST | gRPC |
| --- | ---: | ---: |
| Cuerpo de respuesta | 45 | 15 |
| Flujo cliente → servidor | 129 | 412 |
| Flujo servidor → cliente | 205 | 257 |
| Total de la conexión medida | **334** | **669** |

El cuerpo Protobuf fue tres veces más pequeño, pero la conexión gRPC completa
transmitió más bytes en esta consulta aislada. Los tamaños no equivalen al ejemplo
de 180 bits contra 60 de clase: aquí el cuerpo mide **360 bits contra 120**, y al
contar el intercambio también aparecen encabezados y mensajes de control. Los
totales pueden variar entre ejecuciones por el cierre y control de HTTP/2, las
versiones de las bibliotecas y los valores de encabezados. El `Host`/`:authority`
de esta medición corresponde al puerto local del intermediario.

La salida original se conserva en [evidencia/medicion.json](evidencia/medicion.json).

## Ejecutar sin Docker (opcional)

Con Python 3.12, desde esta carpeta:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python -m grpc_tools.protoc -Iproto --python_out=. --grpc_python_out=. proto/catalog.proto
python server.py
```

En otra terminal, activar el mismo entorno y ejecutar `python client.py`. En este
modo los puertos son 8000 y 50051. Para las pruebas: `python -m unittest -v`.
La verificación registrada para esta entrega se realizó con Docker.

## Archivos y alcance

| Archivo | Responsabilidad |
| --- | --- |
| `service.py` | Catálogo y reglas de negocio compartidas |
| `server.py` | Controladores REST/gRPC y arranque de ambos |
| `client.py` | Consultas y comparación de resultados |
| `proto/catalog.proto` | Contrato tipado de gRPC |
| `Dockerfile.server`, `Dockerfile.client` | Imágenes de servidor y cliente |
| `docker-compose.yml`, `healthcheck.py` | Red y arranque cuando ambos protocolos están listos |
| `test_service.py` | Pruebas del dominio y los transportes |
| `measure.py` | Medición de una conexión por protocolo |
| `EVIDENCIA.md`, `evidencia/` | Resultados reales y comandos de reproducción |

Es un ejercicio local: usa memoria, servidor HTTP didáctico de la biblioteca estándar,
gRPC sin TLS y no tiene autenticación. Los contenedores ejecutan Python con un usuario
sin privilegios. No hay operaciones de escritura ni persistencia que configurar.

## Ubicación de entrega

`entregas/vaquero_pablo/tareas/t01/`, rama `entregas_vaquero_pablo`.
La consigna indica entrega mediante push a esa rama y sin pull request durante
el semestre; fecha de T01: domingo **20 de septiembre de 2026, 23:59**.

## Referencias

- [Consigna T01 en entregas](https://github.com/OscarRuiz21/sd-2027-1/tree/main/entregas).
- [gRPC para Python: generación de código y primeras llamadas](https://grpc.io/docs/languages/python/quickstart/).
- [Docker Compose: esperar un servicio saludable](https://docs.docker.com/compose/how-tos/startup-order/).
