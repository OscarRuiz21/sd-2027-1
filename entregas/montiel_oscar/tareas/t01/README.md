# T01 · El mismo servicio por REST y gRPC

## Objetivo

El proyecto consulta un artículo por ID. La lógica de negocio vive una sola vez en
`CatalogService`, que contiene un diccionario en memoria y el método `get_item`.

Dos controladores usan esa misma lógica:

- REST: `GET /items/{id}` en el puerto `8000`.
- gRPC: `Catalog/GetItem` en el puerto `50051`, definido en `proto/catalog.proto`.

No existen dos programas de negocio: REST traduce una solicitud HTTP a una llamada a
`CatalogService`, y gRPC traduce un mensaje protobuf a esa misma llamada.

## Archivos principales

- `server.py`: lógica compartida y ambos controladores.
- `client.py`: cliente capaz de llamar REST, gRPC o ambos.
- `proto/catalog.proto`: contrato gRPC.
- `Dockerfile.server` y `Dockerfile.client`: imágenes de cada participante.
- `compose.yaml`: declara los dos servicios y la red privada `t01_default`.
- `evidencia.md`: salidas de las pruebas.

## Ejecutar

```bash
docker compose up --build -d server
docker compose run --rm client both 1
docker compose ps
```

La red la crea Compose. El cliente usa el hostname `server`, que es el nombre del
servicio dentro de esa red; no necesita una IP fija.

## Pruebas individuales

```bash
docker compose run --rm client rest 1
docker compose run --rm client grpc 1
curl -i http://localhost:8000/items/999
```

El ID `999` no existe: REST responde `404 Not Found` y gRPC responde `NOT_FOUND`.

## Punto extra: bytes transmitidos

El cliente imprime una medición de nivel aplicación: la línea de petición y el cuerpo
JSON de REST, frente a los mensajes protobuf serializados de gRPC. Es útil para observar
la diferencia entre JSON legible y protobuf binario, pero no cuenta cabeceras ni TCP/IP.

La medición realizada se documenta en `evidencia.md`: para la consulta del artículo
`1`, REST midió 74 B (23 B de línea de petición + 51 B de JSON) y gRPC midió 34 B
(3 B de petición protobuf + 31 B de respuesta protobuf). La diferencia fue de 40 B
a favor de gRPC en los mensajes de aplicación.

La medición no incluye cabeceras HTTP, el encapsulado de gRPC/HTTP2 ni TCP/IP. Por ello
describe el tamaño del contenido intercambiado, no el total de paquetes de red.
