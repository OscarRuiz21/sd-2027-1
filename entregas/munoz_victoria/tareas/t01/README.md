# T01 · Servicio híbrido: REST y gRPC con la misma lógica de negocio

## 1. Diseño y arquitectura

Este proyecto implementa un único componente de lógica de negocio, ubicado en `database.py`, que se expone mediante dos interfaces de comunicación dentro de un mismo servidor (`server.py`).

- **REST API:** utiliza FastAPI sobre HTTP/1.1 y serialización JSON, expuesta en el puerto `8000`.
- **gRPC:** utiliza Protocol Buffers sobre HTTP/2, expuesta en el puerto `50051`.

### ¿Dónde quedó la lógica y qué cambia entre los controladores?

- **Lógica de negocio:** reside exclusivamente en `database.py`, mediante la función `get_item_from_db`. Ambos controladores reutilizan esta función.
- **Controlador REST:** recibe solicitudes HTTP, obtiene el identificador desde la URL y devuelve datos que FastAPI convierte automáticamente en JSON.
- **Controlador gRPC:** recibe mensajes tipados definidos en `service.proto` y responde mediante objetos `ItemResponse`.

Esta arquitectura evita duplicar la lógica de negocio y permite comparar dos mecanismos de comunicación sobre los mismos datos.

---

## 2. Estructura de archivos

- `database.py`: lógica de negocio y datos de prueba en memoria.
- `service.proto`: contrato que define el servicio gRPC y sus mensajes.
- `service_pb2.py` y `service_pb2_grpc.py`: código generado para utilizar gRPC en Python.
- `server.py`: servidor que ejecuta FastAPI y gRPC.
- `client.py`: cliente de prueba para consumir ambas interfaces y medir el tamaño de las respuestas.
- `Dockerfile`: imagen del servidor.
- `Dockerfile.client`: imagen independiente del cliente.
- `docker-compose.yml`: configuración de los servicios, sus puertos, la red y la dependencia entre cliente y servidor.
- `.gitignore`: excluye archivos generados, caché y archivos de entorno.
- `requerimientos.txt`: dependencias de Python.

---

## 3. Instrucciones de ejecución

### Levantar los servicios

Desde la carpeta del proyecto, ejecutar:

```bash
docker compose up --build
```

Docker Compose construye las imágenes, inicia el servidor y ejecuta el cliente cuando el servidor está saludable. El cliente se conecta al servicio mediante el nombre DNS `hybrid-service`.

### Detener los servicios

```bash
docker compose down
```

### Consultas de prueba

El cliente realiza pruebas con dos identificadores:

- ID `1`: verifica la respuesta exitosa.
- ID `99`: verifica el manejo de un elemento inexistente.

---

## 4. Evidencia de funcionamiento

Al ejecutar el cliente dentro de Docker Compose, se obtuvieron los siguientes resultados:

| Interfaz | ID | Resultado | Tamaño medido |
|---|---:|---|---:|
| REST | `1` | HTTP 200, respuesta exitosa | 81 bytes |
| gRPC | `1` | Respuesta exitosa | 45 bytes |
| REST | `99` | HTTP 404, elemento no encontrado | 31 bytes |
| gRPC | `99` | Error `NOT_FOUND` | No aplica a una respuesta exitosa |

Para el ID `1`, ambas interfaces recuperaron los datos del mismo elemento: `Laptop Pro`, con descripción `16GB RAM, 512GB SSD` y precio `1200.5`.

El cliente también comprobó que ambas interfaces notificaran correctamente cuando el elemento con ID `99` no existía.

---

## 5. Medición de bytes: REST frente a gRPC

La medición se realizó desde el cliente de Python usando los siguientes métodos:

- **REST:** `len(response.content)` mide la longitud, en bytes, del cuerpo de la respuesta HTTP recibida.
- **gRPC:** `response.ByteSize()` obtiene el tamaño serializado del mensaje Protobuf recibido.

Para el elemento con ID `1`, se obtuvieron estos valores:

| Métrica | REST | gRPC |
|---|---:|---:|
| Tamaño medido | 81 bytes | 45 bytes |

La diferencia es de 36 bytes. En esta prueba, el mensaje Protobuf medido fue aproximadamente un 44.4 % menor que el cuerpo HTTP medido.

Es importante señalar que esta comparación corresponde al cuerpo de la respuesta HTTP frente al mensaje Protobuf serializado. No representa el tráfico total de red, ya que no incluye todos los encabezados, metadatos ni las sobrecargas de los protocolos de transporte.

### Conclusión del análisis

En esta prueba, gRPC presentó un mensaje serializado más pequeño que el cuerpo de la respuesta REST en JSON. Esto ilustra una ventaja potencial de Protocol Buffers cuando se busca una representación compacta de los datos. Sin embargo, los resultados corresponden a este ejemplo específico y no bastan por sí solos para determinar el rendimiento general de ambas interfaces.

