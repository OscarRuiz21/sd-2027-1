# El mismo servicio. Dos veces — Amy Veraza

Caso de uso único: **consultar un producto por ID**. Las versiones REST y gRPC
usan Python y la misma función de negocio en `catalog.py`, con tres productos
fijos en memoria. El precio se expresa en centavos de MXN para evitar redondeos.
No hay operaciones de escritura ni base de datos.

## Levantar los dos servicios

Requisito: Docker Desktop iniciado, con contenedores Linux y Docker Compose.
Desde la raíz del repositorio:

```powershell
cd entregas/veraza_amy/s04/rest-grpc
docker compose up -d --build --wait
docker compose ps
```

REST queda en `http://localhost:8082` y gRPC en `localhost:50051`. El puerto REST
interno es 8080. Se publica 8082 para convivir con la práctica Docker anterior.
Ambos servicios tienen un Dockerfile propio y una comprobación de salud que
realiza una consulta real. `tools` y `grpcurl` son utilidades opcionales del
perfil `tools`: el levantamiento normal solo inicia los dos servidores.

## Contratos y resultados equivalentes

| Caso | REST | gRPC |
|---|---|---|
| Consultar | `GET /products/{id}` | `catalog.Catalog/GetProduct` |
| ID existente: 1, 2 o 3 | HTTP 200 y producto JSON | OK (0) y mensaje Product |
| ID cero o negativo | HTTP 400 y error JSON | INVALID_ARGUMENT (3) |
| ID válido inexistente, por ejemplo 999 | HTTP 404 y error JSON | NOT_FOUND (5) |
| ID con texto o fuera de int32 | HTTP 400 | El cliente Protobuf rechaza el dato antes de invocar el método |

REST también responde 404 para una ruta inexistente y 405 con `Allow: GET` ante
POST, PUT, PATCH, DELETE u OPTIONS. Un ID válido pertenece al intervalo
1–2147483647. En gRPC omitir el ID produce el valor predeterminado 0 y, por tanto,
INVALID_ARGUMENT.

Contrato REST: [`openapi.yaml`](openapi.yaml). Contrato gRPC:
[`proto/catalog.proto`](proto/catalog.proto). La representación JSON de grpcurl
usa `priceCents`; corresponde al campo Protobuf `price_cents` y representa el
mismo valor que REST. El JSON que muestra grpcurl es una representación para
humanos: la comunicación gRPC usa Protobuf binario.

## Pruebas manuales con curl

En PowerShell se usa `curl.exe` para evitar el alias de Windows PowerShell.
En Linux/macOS sustituir `curl.exe` por `curl`.

```powershell
curl.exe -i http://localhost:8082/products/1
curl.exe -i http://localhost:8082/products/0
curl.exe -i http://localhost:8082/products/999
curl.exe -i http://localhost:8082/products/abc
curl.exe -i -X POST http://localhost:8082/products/1
```

Resultados esperados, en orden: 200, 400, 404, 400 y 405. La respuesta exitosa es:

```json
{"id":1,"name":"Teclado","price_cents":59900,"stock":12}
```

## Pruebas manuales con grpcurl

No hace falta instalar grpcurl: Compose lo ejecuta en un contenedor con acceso
al `.proto`. Estos comandos son para PowerShell; se pasa JSON por entrada
estándar para evitar problemas con las comillas.

```powershell
'{"id":1}' | docker compose run --rm -T grpcurl -d '@' grpc:50051 catalog.Catalog/GetProduct
'{"id":0}' | docker compose run --rm -T grpcurl -d '@' grpc:50051 catalog.Catalog/GetProduct
'{"id":999}' | docker compose run --rm -T grpcurl -d '@' grpc:50051 catalog.Catalog/GetProduct
```

El primero devuelve el producto; los siguientes muestran `InvalidArgument` y
`NotFound` y terminan con código de salida distinto de cero, como corresponde
a esas pruebas negativas. Se usa conexión sin TLS para este laboratorio local.

Si ya tienes grpcurl instalado, desde esta carpeta en Bash:

```bash
grpcurl -plaintext -import-path proto -proto catalog.proto -d '{"id":1}' localhost:50051 catalog.Catalog/GetProduct
```

## Código generado

Se entregan `catalog_pb2.py` (mensajes) y `catalog_pb2_grpc.py` (cliente y base
del servidor), generados realmente con `grpcio-tools==1.71.0`. No se editan a mano.
El Dockerfile gRPC vuelve a generarlos al construir la imagen.
Para regenerar y recuperar los archivos después de modificar el contrato:

```powershell
docker compose up -d --build --wait
docker compose cp grpc:/app/catalog_pb2.py ./catalog_pb2.py
docker compose cp grpc:/app/catalog_pb2_grpc.py ./catalog_pb2_grpc.py
```

El comando de generación que ejecuta Docker es:

```text
python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/catalog.proto
```

Este flujo sigue la [guía oficial de generación para Python](https://grpc.io/docs/languages/python/quickstart/).
Los errores corresponden a los [códigos oficiales de gRPC](https://grpc.io/docs/guides/status-codes/).

## Pruebas automáticas

```powershell
docker compose run --build --rm tools
```

Se verifican los tres productos y sus valores esperados, la equivalencia entre
protocolos, los errores de validación y producto inexistente, los límites de ID,
una ruta desconocida y un verbo no permitido. Resultado observado el 12 de
septiembre de 2026: **4 pruebas aprobadas**, con múltiples casos por prueba.

```text
test_errors ... ok
test_invalid_rest_input ... ok
test_same_products ... ok
test_unknown_route_and_method ... ok
Ran 4 tests in 0.052s
OK
```

También se ejecutaron consultas reales con curl y grpcurl.

## Comparación medida

Ejecución del 12 de septiembre de 2026 en Docker Desktop sobre Windows,
con Python 3.12.14 en contenedores Linux. Resultados guardados en
[`results/benchmark.json`](results/benchmark.json).

| Medida para consultar el producto 1 | REST | gRPC |
|---|---:|---:|
| Mensaje de respuesta, sin cabeceras | 56 bytes JSON | 17 bytes Protobuf |
| Primera consulta, tráfico de aplicación TCP en ambos sentidos | 294 bytes | 668 bytes |
| Consulta con conexión reutilizada, promedio en ambos sentidos | 294 bytes | 142.04 bytes |
| Tiempo promedio | 0.555 ms | 1.427 ms |
| Mediana | 0.517 ms | 1.056 ms |
| Percentil 95 | 1.017 ms | 3.389 ms |
| Líneas físicas del contrato | 59 OpenAPI | 17 proto |
| Líneas no vacías del contrato | 59 | 14 |

### Cómo se midió

`benchmark.py` consulta el mismo producto 100 veces por protocolo, secuencialmente,
después de 10 consultas de calentamiento, reutilizando la conexión. Cronometra
con `perf_counter_ns` e incluye el trabajo del cliente para leer y comprobar la
respuesta. Mide primero REST y después gRPC, sin concurrencia entre solicitudes.

Para los bytes, hace otra ejecución mediante un proxy TCP que cuenta los datos
reales reenviados en cada dirección. Separa la primera consulta de las 100
siguientes, con 200 ms de espera para frames de control pendientes. Incluye
cabeceras HTTP/1.1 o frames HTTP/2, metadatos y framing gRPC; la primera consulta
incluye el establecimiento HTTP/2. Excluye el cierre y las cabeceras TCP/IP y
Ethernet, ACK y retransmisiones: **no es una captura del tamaño total de paquetes
en el cable**. El conteo representa bytes de aplicación transportados por TCP.
La latencia se mide directamente, sin ese proxy. No se usa TLS ni compresión.

El tamaño del mensaje se calcula sobre los bytes JSON recibidos y la
serialización Protobuf recibida. No se confunde con el tráfico completo. El
conteo de líneas lee los archivos tal como están formateados; OpenAPI incluye
respuestas de error explícitas mientras `.proto` usa los estados estándar gRPC.
Por ello, las líneas dependen del formato y de lo que expresa cada contrato.

### Interpretación

En esta ejecución, Protobuf redujo el mensaje de respuesta de 56 a 17 bytes.
gRPC transmitió menos bytes por consulta al reutilizar la conexión, pero tuvo
más tráfico inicial y mayor latencia en este servicio pequeño. No se puede
concluir que un protocolo siempre sea más rápido: influyen el servidor, el
cliente, el calentamiento y la carga de Docker. La medición es ilustrativa,
no una prueba de carga; el equipo también realizaba tareas de preparación.

REST facilita consultar desde navegador o curl y revisar JSON. gRPC ofrece un
contrato compacto y genera los mensajes y el cliente, a cambio de incorporar
el compilador, las dependencias Protobuf/gRPC y herramientas específicas.

### Repetir la medición

Después de construir los servicios y ejecutar las pruebas:

```powershell
docker compose run --rm --no-deps -T tools python benchmark.py
```

Para guardar una nueva ejecución sin sobrescribir la evidencia original:

```powershell
docker compose run --rm --no-deps -T tools python benchmark.py | Out-File -Encoding utf8 results/benchmark-nuevo.json
```

Los tiempos y los frames de control pueden variar entre ejecuciones.

## Detener

```powershell
docker compose down
```
