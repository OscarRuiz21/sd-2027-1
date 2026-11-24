# T01 · Catálogo de libros por REST y gRPC

El servidor recibe el ID de un libro y devuelve su título y año. Los datos están en un diccionario en memoria. Tanto la ruta REST como el método gRPC llaman a `buscar_libro` de `servidor/catalogo.py`. Así, si agrego un libro al diccionario, aparece en las dos interfaces.

REST recibe el ID en `GET /libros?id=L01` y responde con JSON. gRPC recibe una `Consulta` y devuelve un `Libro`, según el contrato en `proto/catalogo.proto`. Si el libro no existe, las respuestas son HTTP `404` y gRPC `NOT_FOUND`.

## Cómo ejecutarlo

Desde esta carpeta, con Docker Compose:

```bash
docker compose --profile cliente build
docker compose up -d --wait servidor
docker compose run --rm cliente
docker compose down
```

La prueba del cliente consulta `L01` y `L99` en ambas interfaces. Para probar otro ID, escribe, por ejemplo, `docker compose run --rm cliente L02` antes de apagar el servidor. Compose conecta los contenedores por el nombre `servidor`. REST usa el puerto 8080 y gRPC usa el 50051 dentro de la red de Docker. Los archivos de Python que salen del `.proto` se generan al construir las imágenes.

## Medición de la consulta de L01

| Formato | Datos de la respuesta | Cliente a servidor | Servidor a cliente | Total TCP |
| --- | ---: | ---: | ---: | ---: |
| REST | 54 B de JSON | 130 B | 220 B | 350 B |
| gRPC | 23 B de Protobuf | 428 B | 265 B | 693 B |

El cliente muestra el tamaño del cuerpo JSON y del mensaje Protobuf. Para el total hice una corrida con `docker compose run --rm -T cliente L01`. Capturé en `eth0` del servidor con `tcpdump`, filtré los puertos 8080 y 50051 y sumé la longitud de los datos TCP en ambos sentidos. La suma incluye los datos de HTTP y de HTTP/2, como sus tramas de inicio. Quedan fuera las cabeceras de TCP, IP y Ethernet.

En esta prueba Protobuf ocupó menos espacio que JSON. La conexión nueva de gRPC intercambió más bytes en total al incluir el arranque y las tramas de HTTP/2. Los resultados de la consulta y las líneas medidas están en [evidencia.txt](evidencia.txt).
