# Tarea 1 - El mismo servicio, por REST y por gRPC

## Qué hace el servicio

El servicio recibe un ID y devuelve la información asociada a ese ID.

Si el ID no existe, responde que no hay datos. Para esta práctica se utiliza un diccionario en memoria como una base de datos sencilla.

La idea principal fue tener **una sola lógica de negocio** y exponerla de dos formas: REST y gRPC.

## Diseño

La lógica de negocio está en:

```text
server/logica.py
```

Ahí se encuentra la función:

```python
buscar_por_id(id)
```

Esta función recibe un ID, busca la información en el diccionario y devuelve el resultado. No depende de REST ni de gRPC.

Los dos controladores utilizan esa misma función:

* `server/rest.py` recibe el ID mediante la ruta `GET /item/<id>`, llama a `buscar_por_id()` y devuelve el resultado como JSON.
* `server/grpc_server.py` recibe un `ItemRequest`, toma el ID, llama a `buscar_por_id()` y construye un `ItemResponse` definido en el archivo `.proto`.

Por lo tanto, la búsqueda y la decisión de si existe o no el ID no están repetidas en los dos controladores. Lo que cambia es la forma en que se recibe la petición y se devuelve la respuesta.

`server/index.py` inicia las dos interfaces en el mismo servidor:

* REST: puerto `3000`
* gRPC: puerto `50051`

## REST y gRPC

La diferencia principal entre las dos interfaces está en la forma de comunicación.

### REST

REST utiliza HTTP y devuelve los datos en formato JSON.

Por ejemplo:

```text
GET /item/1
```

devuelve:

```shell
{
  "encontrado": true,
  "nombre": "Pedro Lopez",
  "area": "Computacion"
}
```

### gRPC

gRPC utiliza el contrato definido en:

```text
proto/item.proto
```

El contrato define el servicio `ItemService`, el método `GetItem` y los mensajes que se utilizan para la petición y la respuesta.

Los archivos `item_pb2.py` e `item_pb2_grpc.py` son generados a partir del `.proto` y permiten que el cliente y el servidor utilicen ese contrato.

A diferencia del JSON utilizado por REST, los mensajes de gRPC se serializan utilizando Protocol Buffers, por lo que los datos se envían en formato binario.

## Estructura del proyecto

La tarea esta organizada en tres carpetas principales:

- `proto`: contiene `item.proto`, el contrato de gRPC.

- `server`: incluye la lógica de negocio, los controladores REST y gRPC, el archivo de inicio y los archivos gRPC generados automáticamente, además de `requirements.txt` y `Dockerfile`.

- `client`: contiene el cliente que prueba ambas interfaces y sus archivos gRPC, `requirements.txt` y `Dockerfile`.

En la raíz están `docker-compose.yml` para conectar los contenedores, `evidencia.md` con las pruebas de funcionamiento y `README.md` con la documentación.

```text
t01/
├── client/
│   ├── cliente.py
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── item_pb2.py
│   └── item_pb2_grpc.py
│
├── server/
│   ├── index.py
│   ├── logica.py
│   ├── rest.py
│   ├── grpc_server.py
│   ├── medir_grpc.py
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── item_pb2.py
│   └── item_pb2_grpc.py
│
├── proto/
│   └── item.proto
│
├── docker-compose.yml
├── evidencia.md
└── README.md
```

Los archivos `item_pb2.py` e `item_pb2_grpc.py` son generados automáticamente a partir del `.proto`.

## Cómo levantar el proyecto

Primero se construyen las imágenes:

```bash
docker build -t t01-servidor ./server
docker build -t t01-cliente ./client
```

Después se levantan los contenedores:

```bash
docker compose up -d
```

En este caso, el `docker-compose.yml` utiliza las imágenes `t01-servidor` y `t01-cliente`, por lo que se construyen antes de ejecutar Compose.

Para revisar los contenedores existentes se uso:

```bash
docker compose ps
```

Para revisar los logs:

```bash
docker compose logs servidor
docker compose logs cliente
```

También se puede probar REST directamente desde la máquina:

```bash
curl http://localhost:3000/item/1
```

Para detener los contenedores:

```bash
docker compose down
```

## Comunicación entre los contenedores

Docker Compose crea una red para los servicios del proyecto.

El cliente se comunica con el servidor utilizando el nombre del servicio: `servidor`

No necesita utilizar la dirección IP interna del contenedor.

Por ejemplo, desde el cliente:

```text
http://servidor:3000
```

y para gRPC:

```text
servidor:50051
```

Esto permite que los dos contenedores se comuniquen dentro de la red creada por Docker Compose.

## Punto extra: medición de bytes

Se hizo una medición del tamaño del contenido de una misma respuesta para comparar REST y gRPC.

Para REST se utilizó `curl` con la opción `-w` y se obtuvo:

```text
64 bytes
```

Para gRPC se utilizó `SerializeToString()` sobre el mensaje `ItemResponse` y se obtuvo:

```text
28 bytes
```

Estas mediciones corresponden al contenido de la respuesta/mensaje y no representan todos los bytes que necesariamente viajaron por la red, ya que no se están contando de la misma manera los encabezados y metadatos de transporte.

La diferencia se debe principalmente a la representación utilizada. JSON contiene los nombres de los campos y caracteres adicionales como comillas, dos puntos y llaves. Protobuf utiliza una representación binaria y los campos se identifican mediante sus números definidos en el `.proto`.

Por eso, para esta respuesta de ejemplo, el mensaje serializado de Protobuf resultó más pequeño que la respuesta JSON.
