# T01 - El mismo servicio por REST y por gRPC

**Alumno:** Fernando Reyes Vázquez

## Descripción

En esta tarea se desarrolló un servicio para consultar información de estudiantes mediante un ID.

Cada estudiante tiene asociados los siguientes datos:

- ID
- nombre
- materia
- correo electrónico
- número de celular

El correo y el celular se utilizan como datos de contacto de ejemplo. Todos los datos utilizados son ficticios y solamente sirven para probar el funcionamiento del servicio.

La misma información puede consultarse mediante REST y gRPC. Las dos interfaces utilizan la misma lógica de negocio, por lo que no existe una búsqueda diferente para cada mecanismo.

---

## Estructura del proyecto

```text
t01/
├── .gitignore
├── README.md
├── evidencia.md
├── docker-compose.yml
│
├── evidencias/
│   ├── 01-build.png
│   ├── 02-servidor.png
│   ├── 03-pruebas-rest-grpc.png
│   └── 04-medicion-bytes.png
│
├── proto/
│   └── servicio.proto
│
├── server/
│   ├── Dockerfile
│   ├── logic.py
│   ├── requirements.txt
│   ├── server.py
│   ├── servicio_pb2.py
│   └── servicio_pb2_grpc.py
│
└── client/
    ├── Dockerfile
    ├── client.py
    ├── medir_bytes.py
    ├── requirements.txt
    ├── servicio_pb2.py
    └── servicio_pb2_grpc.py
```

Los archivos principales son:

- `server/logic.py`: contiene los datos de los estudiantes y la lógica de búsqueda.
- `server/server.py`: contiene los controladores REST y gRPC.
- `client/client.py`: cliente que puede realizar consultas utilizando cualquiera de las dos interfaces.
- `proto/servicio.proto`: contrato utilizado por gRPC.
- `docker-compose.yml`: define los servicios y su comunicación mediante Docker.
- `evidencia.md`: contiene las salidas y capturas de las pruebas realizadas.

---

## Diseño

La lógica de negocio se encuentra en:

```text
server/logic.py
```

Dentro de este archivo se encuentra el diccionario con la información de los estudiantes y la función:

```python
def buscar_usuario(usuario_id):
    return USUARIOS.get(usuario_id)
```

Esta función se escribió una sola vez y es utilizada tanto por REST como por gRPC.

El funcionamiento general es:

```text
                 CLIENTE
                    |
          +---------+---------+
          |                   |
        REST                 gRPC
      puerto 5001         puerto 50051
          |                   |
          +---------+---------+
                    |
             buscar_usuario()
                    |
                logic.py
```

De esta forma, lo que cambia entre las dos interfaces es el mecanismo de comunicación, mientras que la lógica utilizada para consultar la información sigue siendo la misma.

### REST

La interfaz REST se implementó utilizando Flask y funciona en el puerto:

```text
5001
```

La consulta se realiza mediante una ruta como:

```text
GET /usuarios/1
```

Si el estudiante existe, se devuelve su información en formato JSON. Si el ID no existe, se devuelve un código HTTP `404`.

### gRPC

La interfaz gRPC funciona en el puerto:

```text
50051
```

El contrato se encuentra en:

```text
proto/servicio.proto
```

El método definido es:

```protobuf
rpc ObtenerUsuario (UsuarioRequest) returns (UsuarioResponse);
```

El cliente envía el ID mediante un `UsuarioRequest` y recibe los datos mediante un `UsuarioResponse`.

Cuando el ID no existe se utiliza el estado:

```text
NOT_FOUND
```

Los archivos `servicio_pb2.py` y `servicio_pb2_grpc.py` fueron generados a partir del archivo `servicio.proto`.

---

## Cliente

Se desarrolló un solo cliente:

```text
client/client.py
```

Este puede realizar consultas mediante REST o gRPC dependiendo del argumento utilizado.

Por ejemplo:

```bash
python3 client/client.py rest 1
```

o:

```bash
python3 client/client.py grpc 1
```

Por lo tanto, no se crearon clientes independientes para cada mecanismo.

---

## Cómo ejecutar el proyecto

Primero se debe posicionar la terminal en la carpeta de la tarea:

```bash
cd entregas/reyes_fernando/tareas/t01
```

El proyecto puede ejecutarse de dos formas.

### Opción 1. Construir y levantar los servicios

```bash
docker compose up --build
```

Con este comando se construyen las imágenes y se levantan los servicios definidos en `docker-compose.yml`.

Con la configuración actual, el cliente realiza automáticamente la consulta predeterminada:

```text
REST - ID 1
```

Para realizar otras consultas se puede abrir una segunda terminal, posicionarse nuevamente en la carpeta `t01` y ejecutar, por ejemplo:

```bash
docker compose run --rm client rest 99
docker compose run --rm client grpc 1
docker compose run --rm client grpc 99
```

### Opción 2. Construir y levantar únicamente el servidor

También se puede construir primero el proyecto:

```bash
docker compose build
```

y después levantar únicamente el servidor:

```bash
docker compose up -d server
```

Una vez iniciado, se pueden realizar las consultas desde la misma terminal:

```bash
docker compose run --rm client rest 1
docker compose run --rm client rest 99
docker compose run --rm client grpc 1
docker compose run --rm client grpc 99
```

El servidor utiliza:

```text
5001  -> REST
50051 -> gRPC
```

Para detener el proyecto:

```bash
docker compose down
```

Las salidas completas de las pruebas se encuentran en `evidencia.md`.

---

## Comunicación entre contenedores

Docker Compose crea una red para comunicar al cliente con el servidor.

Dentro de esta red, el cliente utiliza el nombre del servicio `server` para localizar al servidor.

Para REST utiliza:

```text
http://server:5001
```

y para gRPC:

```text
server:50051
```

Estas direcciones se proporcionan mediante las variables de entorno definidas en `docker-compose.yml`:

```yaml
environment:
  REST_URL: http://server:5001
  GRPC_HOST: server:50051
```

Esto permite que el cliente y el servidor se comuniquen dentro de la red de Docker sin depender de una dirección IP fija.

---

## Punto extra - comparación del tamaño de las respuestas

También se realizó una comparación del tamaño de la misma respuesta utilizando REST y gRPC.

Para mantener la comparación igual en ambos casos se utilizó:

```text
ID = 1
```

El programa utilizado para realizar la medición se encuentra en:

```text
client/medir_bytes.py
```

y se ejecutó con:

```bash
python3 client/medir_bytes.py
```

### Método utilizado

Para REST se realizó la consulta del estudiante con ID 1 y se midió el tamaño en bytes del cuerpo JSON recibido.

Para gRPC se realizó la misma consulta y se tomó el mensaje `UsuarioResponse`. Después se serializó utilizando Protocol Buffers con:

```python
SerializeToString()
```

y se calculó el tamaño del mensaje resultante.

Los resultados fueron:

```text
REST - cuerpo de respuesta JSON: 116 bytes
gRPC - mensaje Protocol Buffers: 67 bytes
```

La diferencia obtenida fue de:

```text
49 bytes
```

En esta prueba, la representación utilizada por gRPC fue más compacta que la respuesta JSON utilizada por REST.

Una razón de esta diferencia es que JSON incluye en texto los nombres de los campos junto con sus valores, mientras que Protocol Buffers utiliza una representación binaria y los números de campo definidos previamente en el archivo `.proto`.

### Alcance de la medición

La medición corresponde solamente al contenido de la respuesta.

No se incluyeron:

- cabeceras HTTP;
- HTTP/2;
- TCP;
- framing de gRPC;
- ni otros datos utilizados durante el transporte.

Por esta razón, los valores de **116 bytes para REST y 67 bytes para gRPC** representan el tamaño de la información serializada utilizada en la comparación y no el tráfico total generado en la red.

---

## Evidencia

Las salidas y capturas utilizadas para comprobar el funcionamiento del proyecto se encuentran en:

```text
evidencia.md
```

Ahí se incluyen las pruebas de construcción de las imágenes, ejecución del servidor, consultas por REST y gRPC, manejo de IDs inexistentes y la medición utilizada para el punto extra.

---

## Conclusión

Con esta tarea se implementó un mismo servicio utilizando dos mecanismos de comunicación diferentes. Tanto REST como gRPC permiten consultar la misma información de los estudiantes, pero cada uno maneja las solicitudes y respuestas de una forma distinta.

La parte principal del diseño fue mantener la lógica de búsqueda en `logic.py`. Los dos controladores llaman a la misma función `buscar_usuario()`, evitando tener dos implementaciones diferentes de la lógica de negocio.

También se utilizaron contenedores separados para el cliente y el servidor, conectados mediante Docker Compose. El cliente puede realizar consultas mediante REST por el puerto 5001 o mediante gRPC por el puerto 50051.

Finalmente, para la misma consulta se obtuvo un tamaño de 116 bytes en la respuesta JSON de REST y 67 bytes en el mensaje Protocol Buffers utilizado por gRPC. Aunque la medición considera solamente el contenido de la respuesta, permitió observar de forma práctica una diferencia entre las representaciones utilizadas por ambos mecanismos.
