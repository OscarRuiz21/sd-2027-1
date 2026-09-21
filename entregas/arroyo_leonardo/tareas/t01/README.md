# T01 - El mismo servicio por REST y gRPC

Este proyecto consulta un catálogo de productos en memoria por ID. El servidor expone la misma lógica de negocio mediante REST y gRPC; lo único que cambia es el controlador y el formato de comunicación.

## Diseño

`src/catalogo.js` concentra el diccionario de tres productos y exporta `getProductById(id)`. Tanto la ruta Express como el método gRPC invocan esa función, por lo que no hay dos catálogos ni dos implementaciones de la consulta.

| Interfaz | Operación | Producto existente | Producto inexistente |
| --- | --- | --- | --- |
| REST | `GET /productos/:id` | `200` y JSON | `404` y error JSON |
| gRPC | `ProductosService/GetProduct` | respuesta `ProductResponse` | estado `NOT_FOUND` |

El contrato gRPC está en [`proto/productos.proto`](proto/productos.proto). Protobuf define los números y tipos de campo; `@grpc/proto-loader` carga ese contrato para crear el stub del servidor y del cliente.

## Ejecutar con Docker Compose

Desde esta carpeta:

```bash
docker compose up --build -d server
docker compose run --rm client
docker compose logs server
docker compose down
```

El cliente usa `http://server:3000` y `server:50051`: `server` es el nombre del servicio en la red interna creada por Compose, no `localhost`. No se publican puertos al host porque la demostración ocurre completamente entre contenedores.

## Medición de payload

Para la consulta de un producto existente, el servidor escribe una línea `[medicion]` por cada interfaz. En REST cuenta `Buffer.byteLength(JSON.stringify(producto), 'utf8')`; en gRPC cuenta los bytes de `ProductResponse.serialize(producto)`. Es una medición reproducible de los **cuerpos de respuesta**, no del tráfico total: excluye encabezados HTTP, framing HTTP/2, TCP/IP, conexión y cualquier compresión. La diferencia se debe a que JSON manda los nombres de los campos, mientras Protobuf usa los números declarados en el `.proto`.

La salida de la validación está en [`evidencia.md`](evidencia.md).

## Archivos relevantes

- `Dockerfile.server` y `Dockerfile.client`: imágenes independientes.
- `compose.yaml`: red de Compose, arranque y verificación de salud del servidor.
- `src/server.js`: controladores REST y gRPC, y registro de mediciones.
- `src/client.js`: consultas comparables para IDs existente e inexistente.
