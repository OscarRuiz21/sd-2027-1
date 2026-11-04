# T01 · Un catálogo por REST y por gRPC

Derek Santana Cardoso · Sistemas Distribuidos 2027-1.
Implementación preparada con asistencia de IA. Repaso y explicación personal
pendientes; no se afirma dominio personal por haber ejecutado las pruebas.

## Diseño

`logica.consultar(id)` es el único caso de uso y el único dueño del diccionario.
El servidor ejecuta dos adaptadores **en el mismo proceso**: REST traduce a JSON
y HTTP 200/404; gRPC traduce a Protobuf y OK/NOT_FOUND. No hay una segunda copia
de la lógica de negocio. Los dos productos se mantienen en memoria, como permite
el enunciado. El caso sin datos se representa con un error, no con un producto vacío.

`catalogo.proto` define `Catalogo.Consultar` y los mensajes. Los stubs se generan
durante el build con `grpc_tools.protoc`; no se editan a mano. El cliente llama a
las dos interfaces para IDs 1, 2 y 999 y compara resultados.

## Ejecutar

```bash
docker compose up --build --abort-on-container-exit --exit-code-from cliente
docker compose down
```

Servidor y cliente se comunican por la red privada del proyecto Compose, usando
el nombre DNS `servidor`. No requieren puertos públicos. Hay un Dockerfile para
cada uno. La evidencia real se conserva en `evidencias/`.

## Bytes

El cliente muestra `len(cuerpo_json)` y `len(mensaje.SerializeToString())` para la
misma respuesta. Esto compara únicamente las representaciones del mensaje; no
es una medición del total transmitido por HTTP/1, HTTP/2, TCP o TLS. Por ello no
se presenta como cumplimiento del punto extra de tráfico real. No supongo que
la relación didáctica de 180/60 bits aparezca en una implementación real.

## Para explicar en clase

Cambiar el transporte modifica el contrato, la serialización y cómo se expresa
un error. La búsqueda del producto permanece en la misma función. Si mañana
agrego una base de datos, puedo cambiar `logica.py` sin duplicar esa búsqueda en
los controladores.

Fuente de generación y uso de stubs:
[gRPC, Python Quickstart](https://grpc.io/docs/languages/python/quickstart/).

Revalidación asistida del 3 de noviembre: el cliente Docker terminó con código 0
y comprobó ambos productos y el caso inexistente. Registro en
`evidencias/revalidacion-2026-11-03.txt`.
