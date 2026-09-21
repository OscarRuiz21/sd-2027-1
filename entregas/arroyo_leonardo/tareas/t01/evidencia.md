# Evidencia de ejecución

## Validación funcional local

Se arrancó el mismo servidor con `node src/server.js` y se ejecutó el cliente con las direcciones `127.0.0.1:3000` y `127.0.0.1:50051`. El resultado fue:

```text
--- Producto existente ---
REST id=1 -> HTTP 200: {"id":1,"nombre":"Teclado mecanico","precio":1499,"disponible":true}
gRPC id=1 -> OK: {"id":1,"nombre":"Teclado mecanico","precio":1499,"disponible":true}
--- Producto inexistente ---
REST id=999 -> HTTP 404: {"error":"PRODUCT_NOT_FOUND","message":"No existe el producto con id 999"}
gRPC id=999 -> NOT_FOUND (5): No existe el producto con id 999
```

Los registros del servidor para el producto existente confirmaron los payloads comparables:

```text
[medicion] REST id=1 estado=200 payload=68 bytes
[medicion] gRPC id=1 estado=OK payload=31 bytes
```

## Validación en Docker Compose

Con Docker Desktop activo se ejecutó:

```bash
docker compose up --build -d server
docker compose run --rm client
docker compose logs server
docker compose down
```

El servicio `server` quedó `healthy` y el cliente, conectado mediante los nombres internos `server:3000` y `server:50051`, produjo:

```text
--- Producto existente ---
REST id=1 -> HTTP 200: {"id":1,"nombre":"Teclado mecanico","precio":1499,"disponible":true}
gRPC id=1 -> OK: {"id":1,"nombre":"Teclado mecanico","precio":1499,"disponible":true}
--- Producto inexistente ---
REST id=999 -> HTTP 404: {"error":"PRODUCT_NOT_FOUND","message":"No existe el producto con id 999"}
gRPC id=999 -> NOT_FOUND (5): No existe el producto con id 999
```

Los logs del servidor dentro del contenedor confirmaron:

```text
[medicion] REST id=1 estado=200 payload=68 bytes
[medicion] gRPC id=1 estado=OK payload=31 bytes
[medicion] REST id=999 estado=404 payload=74 bytes
```

Esto confirma que ambos controladores usan la misma lógica, devuelven el mismo producto, manejan la ausencia con `404`/`NOT_FOUND` y registran la medición solicitada.
