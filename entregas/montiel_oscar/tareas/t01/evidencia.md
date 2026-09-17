# Evidencia — T01: REST y gRPC

## REST: ID existente

Comando:

```bash
docker compose run --rm client rest 1
```

Salida:

```text
=== REST ===
Respuesta: {'id': '1', 'name': 'Teclado mecánico', 'price': 899.0}
Bytes de línea de petición REST: 23
Bytes de cuerpo JSON REST: 51
```

## gRPC: ID existente

Comando:

```bash
docker compose run --rm client grpc 1
```

Salida:

```text
=== gRPC ===
Respuesta: {'id': '1', 'name': 'Teclado mecánico', 'price': 899.0}
Bytes de petición protobuf: 3
Bytes de respuesta protobuf: 31
```

## REST: ID inexistente

Comando:

```bash
curl -i http://localhost:8000/items/999
```

Salida relevante:

```text
HTTP/1.1 404 Not Found
{"detail":"No hay datos para ese ID"}
```

## gRPC: ID inexistente

Comando:

```bash
docker compose run --rm client grpc 999
```

Salida:

```text
=== gRPC ===
Error gRPC: NOT_FOUND: No hay datos para ese ID
```

## Estado de Compose

Comando:

```bash
docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"
```

Salida:

```text
NAME           SERVICE   STATUS              PORTS
t01-server-1   server    Up About a minute   0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp, 0.0.0.0:50051->50051/tcp, [::]:50051->50051/tcp
```

## Punto extra: medición de bytes

La medición se hizo en la capa de aplicación, serializando la misma consulta del artículo
`1` desde el cliente y midiendo los bytes antes de enviarlos y después de recibirlos.

| Protocolo | Petición | Respuesta | Total medido |
|---|---:|---:|---:|
| REST | 23 B, línea `GET /items/1 HTTP/1.1` | 51 B, cuerpo JSON | **74 B** |
| gRPC | 3 B, mensaje protobuf | 31 B, mensaje protobuf | **34 B** |

gRPC usa 40 B menos en esta medición de mensajes de aplicación. REST manda JSON legible
y una ruta textual; gRPC usa protobuf binario, que representa los campos con menos bytes.
No se contabilizaron las cabeceras HTTP, el encapsulado de gRPC/HTTP2, ni TCP/IP; por eso
estos totales no representan todo el tráfico de red, sino el contenido de la llamada que
ambos protocolos intercambian.
