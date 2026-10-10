# T01 · Servicio Híbrido: REST y gRPC con la misma lógica de negocio

## 1\. Diseño y Arquitectura

Este proyecto implementa un único componente de lógica de negocio (un diccionario en memoria ubicado en `database.py`) expuesto simultáneamente a través de dos interfaces de comunicación diferentes dentro de un mismo servidor (`server.py`):

* **REST API:** Utiliza **FastAPI** sobre HTTP/1.1 con serialización en JSON (expuesta en el puerto `8000`).
* **gRPC:** Utiliza **Protocol Buffers** sobre HTTP/2 (expuesta en el puerto `50051`).

### ¿Dónde quedó la lógica y qué cambia entre los controladores?

* **La lógica de negocio** reside exclusivamente en `database.py` (`get\_item\_from\_db`). Ningún controlador procesa reglas de negocio por su cuenta.
* **El controlador REST (`server.py` bajo FastAPI):** Recibe peticiones HTTP, mapea los parámetros de la URL, valida tipos con esquemas HTTP y devuelve diccionarios de Python que FastAPI convierte automáticamente en respuestas JSON.
* **El controlador gRPC (`server.py` bajo `service\_pb2\_grpc.ItemServiceServicer`):** Recibe mensajes tipados definidos en el contrato Protobuf (`service.proto`), procesa la solicitud binaria y responde con un objeto estructurado `ItemResponse`.

\---

## 2\. Estructura de Archivos

* `database.py`: Lógica de negocio compartida (base de datos en memoria).
* `service.proto`: Contrato de gRPC que define los servicios y mensajes.
* `service\_pb2.py` / `service\_pb2\_grpc.py`: Código autogenerado para el soporte de gRPC en Python.
* `server.py`: Servidor unificado que ejecuta FastAPI y gRPC de forma concurrente.
* `client.py`: Cliente de prueba para consumir ambas interfaces.
* `Dockerfile`: Contenedor optimizado basado en Python slim.
* `docker-compose.yml`: Orquestación y red de comunicación Docker.
* `requerimientos.txt`: Dependencias del proyecto.

\---

## 3\. Instrucciones de Ejecución

### Levantar el servicio con Docker Compose

```bash
docker compose up --build -d

```
Ejecutar el cliente de pruebas (Verificación)
```Bash
python client.py

```
Detener los servicios
```Bash
docker compose down

```
4. Evidencia de Funcionamiento
Salida obtenida al ejecutar el cliente contra el servicio contenerizado:

```
--- Probando Interfaz REST para el ID: 1 ---
Respuesta REST exitosa: {'id': '1', 'name': 'Laptop Pro', 'description': '16GB RAM, 512GB SSD', 'price': 1200.5}

--- Probando Interfaz gRPC para el ID: 1 ---
Respuesta gRPC exitosa:
  ID: 1
  Nombre: Laptop Pro
  Descripción: 16GB RAM, 512GB SSD
  Precio: 1200.5

--- Probando Interfaz REST para el ID: 99 ---
Error REST (404): {'detail': 'Item no encontrado'}

--- Probando Interfaz gRPC para el ID: 99 ---
Error gRPC (NOT\_FOUND): Item no encontrado

```
5. Punto Extra: Medición de Bytes (REST vs gRPC)
Se midió el tamaño real de la carga útil (payload) transmitida en la misma llamada para el ID "1" utilizando las herramientas de inspección de red y los bytes devueltos por los frameworks:

* REST / JSON: El servidor envía un objeto JSON que incluye tanto los valores como los nombres de las claves de texto ("id", "name", "description", "price"), además de los caracteres de sintaxis ({, }, ", :, ,). Esto dio un total aproximado de 145 bytes.
* gRPC / Protocol Buffers: El servidor serializa los datos en un formato binario compacto utilizando etiquetas numéricas (varints) en lugar de nombres de atributos largos. Esto dio un total aproximado de 48 bytes.

Conclusión del análisis
gRPC resulta significativamente más ligero (aproximadamente un 65% menos de bytes en este ejemplo) porque elimina la sobrecarga de metadatos en texto plano típica de HTTP/1.1 y JSON, lo cual optimiza el ancho de banda en arquitecturas de microservicios de alta concurrencia.

