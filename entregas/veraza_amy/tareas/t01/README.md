# T01 — El mismo servicio por REST y gRPC

Amy Veraza. Entrega: domingo 20 de septiembre de 2026, 23:59.
Ruta: `entregas/veraza_amy/tareas/t01/`.

## Diseño

Un único proceso servidor (`server.py`) expone el catálogo por REST en el puerto
8080 y por gRPC en 50051. **La lógica compartida está en `catalog.py`:** recibe
un ID, valida su rango, consulta un diccionario de tres productos y devuelve
el producto o un error. El precio está en centavos de MXN.

`rest_server.py` convierte la petición HTTP en una llamada a `get_product` y
traduce el resultado a JSON y códigos HTTP. `grpc_server.py` llama a esa misma
función y traduce el resultado a mensajes Protobuf y estados gRPC. Lo único
que cambia entre interfaces es el controlador y el mecanismo de comunicación.

`client.py` recibe un ID y consulta ambas interfaces, mostrando sus respuestas.
Cliente y servidor tienen Dockerfiles propios y se comunican usando el nombre
`server` en la red creada por Compose. El cliente termina después de consultar;
el servidor permanece activo. El cliente no contiene el catálogo ni su lógica.

## Levantar y ejecutar

Requisito: Docker Desktop iniciado con contenedores Linux y Docker Compose.
Desde la raíz del repositorio, en PowerShell:

```powershell
cd entregas/veraza_amy/tareas/t01
 docker compose up -d --build
 docker compose logs client
 docker compose ps -a
```

El cliente espera a que ambas interfaces superen la comprobación de salud.
Es normal que aparezca como `Exited (0)` después de mostrar las dos respuestas.
REST queda accesible en http://localhost:8082/products/1 y gRPC en localhost:50051.
El puerto REST externo evita el 8081 usado por el laboratorio Docker anterior.

Para consultar otros IDs desde el cliente contenerizado:

```powershell
 docker compose run --rm client python client.py 2
 docker compose run --rm client python client.py 0
 docker compose run --rm client python client.py 999
```

## Contratos

| Caso | REST | gRPC |
|---|---|---|
| Consulta | GET /products/{id} | catalog.Catalog/GetProduct |
| ID 1, 2 o 3 | HTTP 200 y producto JSON | OK (0) y Product |
| ID cero o negativo | HTTP 400 | INVALID_ARGUMENT (3) |
| ID válido inexistente | HTTP 404 | NOT_FOUND (5) |
| Texto o valor fuera de int32 | HTTP 400 | El cliente Protobuf rechaza el dato |

El ID válido pertenece a 1–2147483647. Omitirlo en gRPC equivale a 0. REST
responde 404 para rutas desconocidas y 405 con `Allow: GET` para POST, PUT,
PATCH, DELETE y OPTIONS. Los contratos están en [openapi.yaml](openapi.yaml)
y [proto/catalog.proto](proto/catalog.proto).

## Pruebas con curl y grpcurl

En Windows PowerShell usar `curl.exe`; en Bash, `curl`.

```powershell
curl.exe -i http://localhost:8082/products/1
curl.exe -i http://localhost:8082/products/0
curl.exe -i http://localhost:8082/products/999
curl.exe -i http://localhost:8082/products/abc
curl.exe -i -X POST http://localhost:8082/products/1
```

Estados esperados en orden: 200, 400, 404, 400 y 405. Respuesta exitosa:

```json
{"id":1,"name":"Teclado","price_cents":59900,"stock":12}
```

Los siguientes comandos PowerShell usan grpcurl en Docker; no requieren instalarlo:

```powershell
'{"id":1}' | docker compose run --rm -T grpcurl -d '@' server:50051 catalog.Catalog/GetProduct
'{"id":0}' | docker compose run --rm -T grpcurl -d '@' server:50051 catalog.Catalog/GetProduct
'{"id":999}' | docker compose run --rm -T grpcurl -d '@' server:50051 catalog.Catalog/GetProduct
```

El primero muestra el producto. Los otros muestran `InvalidArgument` y `NotFound`
y terminan con código distinto de cero, esperado en estas pruebas negativas.
El JSON mostrado por grpcurl usa `priceCents`, equivalente a `price_cents`;
lo transmitido es Protobuf binario. Se usa conexión sin TLS para el laboratorio.

## Código generado y pruebas automáticas

Se entregan `catalog_pb2.py` y `catalog_pb2_grpc.py`, generados con grpcio-tools
1.71.0. Ambos Dockerfiles regeneran sus mensajes y stubs desde el mismo `.proto`:

```text
python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/catalog.proto
```

Para actualizar las copias entregadas tras cambiar el contrato:

```powershell
docker compose up -d --build
docker compose cp server:/app/catalog_pb2.py ./catalog_pb2.py
docker compose cp server:/app/catalog_pb2_grpc.py ./catalog_pb2_grpc.py
```

Las pruebas de integración usan un cliente real para verificar los tres productos,
los errores equivalentes, los límites del ID y las rutas y métodos incorrectos:

```powershell
docker compose run --rm client python -m unittest -v test_services
```

Evidencia de ejecución en [results/evidencia.txt](results/evidencia.txt).
La generación sigue la [guía oficial de Python](https://grpc.io/docs/languages/python/quickstart/)
y el manejo de errores usa los [estados oficiales de gRPC](https://grpc.io/docs/guides/status-codes/).

## Comparación de bytes y tiempo

La medición actual está en [results/benchmark-t01.json](results/benchmark-t01.json).
Resultados del 12 de septiembre de 2026, 21:43 de Ciudad de México:

| Medida | REST | gRPC |
|---|---:|---:|
| Mensaje de respuesta | 56 bytes | 17 bytes |
| Primera consulta, ambos sentidos, sobre TCP | 294 bytes | 668 bytes |
| Consulta con conexión reutilizada, promedio, ambos sentidos | 294 bytes | 142.04 bytes |
| Latencia promedio | 0.830 ms | 1.819 ms |
| Mediana | 0.661 ms | 1.230 ms |
| Percentil 95 | 2.539 ms | 5.080 ms |

En esta ejecución REST respondió más rápido; gRPC transmitió menos bytes con
la conexión reutilizada, pero más durante la primera consulta.

Se conserva [results/benchmark.json](results/benchmark.json) como evidencia de la
versión inicial con dos contenedores servidor; sus cifras no corresponden a la
estructura final de T01.

Para repetir la medición, con el servidor activo:

```powershell
docker compose run --rm -T client python benchmark.py
```

`benchmark.py` hace 10 consultas de calentamiento y luego 100 consultas secuenciales
por interfaz, con conexión reutilizada. Cronometra la llamada y la comprobación
del resultado usando `perf_counter_ns`, sin proxy. Mide REST antes de gRPC;
no es una prueba de carga ni permite concluir qué protocolo siempre será más rápido.

Para los bytes realiza otra ejecución mediante un proxy TCP que cuenta datos
reales reenviados en cada dirección: una primera consulta y luego 100 más en
la misma conexión. Espera 200 ms para frames de control pendientes. Incluye
cabeceras HTTP/1.1 o frames HTTP/2, metadatos y framing gRPC; la primera llamada
incluye el establecimiento HTTP/2. Excluye cierre, cabeceras TCP/IP y Ethernet,
ACK y retransmisiones. **Son bytes de aplicación transportados por TCP, no el
tamaño total de los paquetes en el cable.** No se usa TLS ni compresión.

El mensaje de respuesta ocupa 56 bytes JSON o 17 bytes Protobuf; este tamaño
no incluye cabeceras. El contrato OpenAPI tiene 59 líneas físicas y no vacías;
el proto tiene 17 físicas y 14 no vacías. OpenAPI declara explícitamente las
respuestas de error, mientras gRPC usa sus estados estándar. La comparación de
líneas depende del formato y de lo que expresa cada contrato.

REST permite inspeccionar fácilmente el servicio desde navegador o curl.
gRPC aporta mensajes tipados y generación de cliente, pero requiere compilador,
dependencias y herramientas específicas. El mensaje binario es más pequeño;
el tráfico completo también depende de la conexión y de los frames de control.
Por eso las cifras difieren del ejemplo didáctico de bits presentado en clase.

## Detener

```powershell
docker compose down
```
