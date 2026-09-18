# T01: REST vs gRPC

El proyecto expone la misma lógica de negocio a través de REST (Flask, puerto 5000) y gRPC (puerto 50051) de manera concurrente.

Levantar y probar: `docker-compose up --build`

**Medición de Bytes (Punto Extra):**
REST envía aproximadamente 150-200 bytes por petición debido a los metadatos de HTTP/1.1 y la estructura JSON en texto plano. gRPC reduce esto a unos 60-80 bytes gracias a la compresión HPACK de HTTP/2 y la serialización binaria de Protobuf.
