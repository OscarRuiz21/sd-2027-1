# T01 — El mismo servicio por REST y gRPC

## 1. Descripción

Esta tarea implementa un mismo servicio de consulta de personas expuesto mediante dos mecanismos de comunicación:

* REST mediante HTTP y JSON.
* gRPC mediante Protocol Buffers.

Ambas interfaces utilizan la misma lógica de negocio, ubicada en `server/service.py`.

El servicio recibe un ID y devuelve la información asociada. Si el ID no existe, devuelve el mensaje `"No hay datos"`.

---

## 2. Arquitectura

La aplicación está organizada de la siguiente manera:

```text
                    t01-server
                        │
                ┌───────┴───────┐
                │               │
              REST             gRPC
             :8000            :50051
                │               │
                └───────┬───────┘
                        │
                 service.py
                 lógica de negocio
                        │
                    t01-client
```

La lógica de negocio no se duplica. Tanto REST como gRPC llaman a la función:

```text
buscar_por_id(id)
```

ubicada en:

```text
server/service.py
```

---

## 3. Estructura del proyecto

```text
t01/
├── server/
│   ├── service.py
│   ├── rest.py
│   ├── grpc_server.py
│   ├── main.py
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── __init__.py
│   └── generated/
│       ├── __init__.py
│       ├── servicio_pb2.py
│       └── servicio_pb2_grpc.py
│
├── client/
│   ├── cliente.py
│   ├── medir_bytes.py
│   ├── requirements.txt
│   └── Dockerfile
│
├── proto/
│   └── servicio.proto
│
├── compose.yaml
└── README.md
```

---

## 4. Lógica de negocio

La información se almacena en un diccionario en memoria:

```python
personas = {
    1: {"id": 1, "nombre": "Alex"},
    2: {"id": 2, "nombre": "Diego"},
    3: {"id": 3, "nombre": "Maria"}
}
```

La función `buscar_por_id()` recibe el ID y busca la información correspondiente.

Si existe, devuelve la persona.

Si no existe, devuelve `None`.

---

## 5. API REST

La interfaz REST utiliza FastAPI.

Endpoint:

```text
GET /personas/{id}
```

Ejemplo:

```text
GET http://localhost:8000/personas/1
```

Respuesta:

```json
{
  "id": 1,
  "nombre": "Alex"
}
```

Para un ID inexistente:

```json
{
  "mensaje": "No hay datos"
}
```

El servidor REST utiliza el puerto:

```text
8000
```

---

## 6. API gRPC

La interfaz gRPC utiliza el contrato definido en:

```text
proto/servicio.proto
```

El servicio definido es:

```text
PersonaService
```

con el método:

```text
ObtenerPersona
```

La solicitud contiene un ID:

```protobuf
message PersonaRequest {
    int32 id = 1;
}
```

La respuesta contiene:

```protobuf
message PersonaResponse {
    int32 id = 1;
    string nombre = 2;
    string mensaje = 3;
}
```

El servidor gRPC utiliza el puerto:

```text
50051
```

Los archivos Python generados a partir del `.proto` se encuentran en:

```text
server/generated/
```

---

## 7. Archivos generados por gRPC

A partir del archivo:

proto/servicio.proto

se generaron los archivos Python:

server/generated/servicio_pb2.py
server/generated/servicio_pb2_grpc.py

Estos archivos son generados automáticamente mediante grpcio-tools.

servicio_pb2.py contiene las clases relacionadas con los mensajes definidos en el .proto.

servicio_pb2_grpc.py contiene las clases necesarias para el cliente y servidor gRPC.

Estos archivos permiten utilizar desde Python el contrato definido en Protocol Buffers.

## 8. Servidor

El archivo:

server/main.py

se encarga de iniciar simultáneamente los dos mecanismos de comunicación.

El servidor inicia:

REST → puerto 8000
gRPC → puerto 50051

Para REST se utiliza Uvicorn junto con FastAPI.

Para gRPC se utiliza el servidor proporcionado por grpcio.

La ejecución conjunta permite que el mismo proceso de aplicación pueda atender solicitudes mediante ambos protocolos.

---

## 9. Cliente

El cliente se encuentra en:

```text
client/cliente.py
```

El programa realiza una consulta mediante REST y otra mediante gRPC.

Para REST utiliza:

```text
requests
```

Para gRPC utiliza:

```text
grpcio
```

El cliente puede utilizar la variable de entorno:

```text
SERVIDOR
```

Cuando se ejecuta localmente, utiliza `localhost`.

Cuando se ejecuta mediante Docker Compose, utiliza:

```text
SERVIDOR=server
```

Esto permite que el cliente encuentre al contenedor del servidor mediante el nombre del servicio de Compose.

---

## 10. Dockerfile del servidor

El archivo:

server/Dockerfile

construye la imagen del servidor a partir de:

python:3.14-slim

Se instalan las dependencias:

fastapi
uvicorn
grpcio
protobuf

El contenedor expone:

8000
50051

y ejecuta:

python -m server.main

De esta manera, al iniciar el contenedor se levantan tanto REST como gRPC.

## 11. Dockerfile del cliente

El archivo:

client/Dockerfile

también utiliza:

python:3.14-slim

Se instalan las dependencias necesarias para realizar las llamadas:

requests
grpcio
protobuf

El cliente también copia los archivos generados de gRPC del servidor porque necesita utilizar las clases definidas por el contrato .proto.

El comando de inicio es:

python client/cliente.py


## 12. Docker Compose

El archivo:

compose.yaml

permite ejecutar el proyecto utilizando dos contenedores:

t01-server
t01-client

La arquitectura queda:

┌─────────────────────────────────────────────┐
│              Docker Compose                 │
│                                             │
│   ┌──────────────┐      ┌──────────────┐   │
│   │ t01-server   │      │ t01-client   │   │
│   │              │      │              │   │
│   │ REST :8000   │◄─────│ Cliente REST │   │
│   │ gRPC :50051  │◄─────│ Cliente gRPC │   │
│   │              │      │              │   │
│   └──────────────┘      └──────────────┘   │
│             │                               │
│       t01_default                           │
│         red Docker                          │
└─────────────────────────────────────────────┘

Docker Compose crea automáticamente una red llamada:

t01_default

Dentro de esta red, el cliente puede comunicarse con el servidor utilizando:

server

Por ejemplo:

http://server:8000/personas/1

y:

server:50051

para gRPC.

No es necesario utilizar localhost entre los contenedores.

## 13. Problema de inicio del cliente y solución

Durante las primeras pruebas ocurrió un problema importante.

El cliente intentaba conectarse al servidor inmediatamente después de que Docker iniciara ambos contenedores.

El error obtenido fue:

ConnectionRefusedError: [Errno 111] Connection refused

y posteriormente:

HTTPConnectionPool(host='server', port=8000)

El motivo fue que el contenedor del servidor ya había sido creado, pero la aplicación todavía estaba iniciando FastAPI y gRPC.

Inicialmente se utilizaba:

depends_on:
  - server

Esto solamente controla el orden de inicio de los contenedores. No garantiza que la aplicación dentro del servidor ya esté lista para recibir solicitudes.

Solución

Se agregó un healthcheck al servicio server:

healthcheck:
  test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/personas/1')"]
  interval: 5s
  timeout: 3s
  retries: 5

Después se modificó depends_on:

depends_on:
  server:
    condition: service_healthy

De esta manera, Docker Compose espera a que REST responda correctamente antes de iniciar el cliente.

Durante la prueba final se observó:

Container t01-server Waiting

después:

Container t01-server Healthy

y posteriormente:

t01-client | Consulta mediante REST:

Esto comprobó que el cliente comenzó después de que el servidor estuviera disponible.

## 14. Problema con la dependencia protobuf

Durante la primera ejecución de Docker Compose apareció otro problema.

El cliente mostró:

ModuleNotFoundError: No module named 'google'

El error se produjo al importar:

server/generated/servicio_pb2.py

Los archivos generados por Protocol Buffers necesitan la biblioteca protobuf.

El client/requirements.txt inicialmente contenía:

requests
grpcio

Se agregó:

protobuf

por lo que quedó:

requests
grpcio
protobuf

Después se reconstruyó la imagen del cliente:

docker compose build client

Al volver a ejecutar Compose, el cliente pudo importar correctamente los archivos generados y realizar las consultas gRPC.

## 15. Problema con los imports de los archivos generados

Otro problema encontrado durante el desarrollo ocurrió al intentar importar:

servicio_pb2_grpc.py

El archivo generado utilizaba internamente:

import servicio_pb2

pero Python no encontraba directamente ese módulo porque se encontraba dentro de:

server/generated/

Para resolverlo se agregó la carpeta generated a las rutas de búsqueda de Python mediante sys.path.

También se agregaron los archivos:

server/__init__.py
server/generated/__init__.py

Esto permitió utilizar correctamente:

from server.generated import servicio_pb2
from server.generated import servicio_pb2_grpc

La solución permitió que el servidor y el cliente utilizaran los archivos generados por gRPC correctamente.

## 16. Prueba del servicio

Se realizaron pruebas primero de forma local y posteriormente dentro de Docker.

Para un ID existente:

ID: 1

el resultado mediante REST fue:

{'id': 1, 'nombre': 'Alex'}

El resultado mediante gRPC fue:

id: 1, nombre: Alex

Esto demuestra que ambos mecanismos proporcionan la misma información.

También se realizó anteriormente una prueba con:

ID: 99

que no existe en el diccionario.

REST devolvió:

{
  "mensaje": "No hay datos"
}

y gRPC devolvió:

No hay datos

Esto demuestra que ambos mecanismos manejan también el caso de un ID inexistente.

## 17. Prueba mediante Docker Compose

La prueba final se realizó mediante:

docker compose up

El servidor inició:

Servidor REST iniciado en el puerto 8000
Servidor gRPC iniciado en el puerto 50051

Posteriormente Docker indicó:

Container t01-server Healthy

y el cliente realizó:

Consulta mediante REST:
{'id': 1, 'nombre': 'Alex'}

Consulta mediante gRPC:
id: 1, nombre: Alex

Finalmente:

t01-client exited with code 0

El código 0 indica que el cliente terminó correctamente y sin errores.

## 18. Medición de bytes

Como parte adicional de la tarea se realizó una comparación del tamaño de los mensajes de REST y gRPC.

La herramienta utilizada se encuentra en:

client/medir_bytes.py

La prueba se realizó utilizando el mismo ID:

1
REST

La respuesta obtenida fue:

{"id":1,"nombre":"Alex"}

El tamaño medido fue:

24 bytes
gRPC

La solicitud Protocol Buffers tuvo:

2 bytes

La respuesta tuvo:

8 bytes

Por lo tanto:

2 + 8 = 10 bytes

El resultado fue:

Método	Solicitud	Respuesta	Total medido
REST	—	        24 bytes	24 bytes
gRPC	2 bytes	     8 bytes	10 bytes

Consideración sobre la medición

Esta medición corresponde al payload de aplicación serializado.

No representa todos los bytes que realmente recorren la red.

No se incluyeron elementos como:

TCP/IP
HTTP
HTTP/2
encabezados
otras capas de transporte

Por lo tanto, el resultado debe interpretarse como una comparación del tamaño de los mensajes de aplicación utilizados en esta prueba.

## 19. Comandos utilizados para construir y ejecutar
Construir las imágenes

Desde: entregas/carrasco_alexander/tareas/t01

se puede ejecutar:

docker compose build

Esto construye las imágenes del servidor y del cliente.

Levantar el proyecto
docker compose up

Esto crea la red y los contenedores y muestra sus logs.

También se puede utilizar:

docker compose up -d

para ejecutarlos en segundo plano.

Revisar los contenedores
docker ps

Permite comprobar si el servidor está ejecutándose.

Revisar el estado de Compose
docker compose ps

Permite comprobar el estado de los servicios del proyecto.

Revisar los logs
docker compose logs

Para revisar solamente el servidor:

docker compose logs server
Detener y eliminar los contenedores
docker compose down

Esto elimina los contenedores y la red del proyecto.

## 20. Puertos utilizados
Servicio	Puerto	Uso
REST	8000	API HTTP
gRPC	50051	Comunicación gRPC

El cliente utiliza estos puertos cuando se comunica con el servidor.

Dentro de Docker Compose utiliza el nombre:

server

Por lo tanto:

REST → server:8000
gRPC → server:50051

Desde el equipo anfitrión se pueden utilizar:

localhost:8000
localhost:50051


## 21. Dependencias
Servidor

Archivo:

server/requirements.txt

Contiene:

fastapi
uvicorn
grpcio
protobuf

Estas dependencias permiten:

Crear la API REST.
Ejecutar el servidor HTTP.
Ejecutar el servidor gRPC.
Trabajar con Protocol Buffers.
Cliente

Archivo:

client/requirements.txt

Contiene:

requests
grpcio
protobuf

Estas dependencias permiten:

Realizar solicitudes HTTP.
Realizar llamadas gRPC.
Utilizar los mensajes generados por Protocol Buffers.


## 22. Cómo ejecutar el proyecto desde cero

Primero se debe entrar a:

entregas/carrasco_alexander/tareas/t01

Después:

docker compose build

Una vez terminada la construcción:

docker compose up

Docker creará la red:

t01_default

y los contenedores:

t01-server
t01-client

El servidor esperará hasta estar saludable y posteriormente el cliente realizará las consultas.

El resultado esperado es:

Consulta mediante REST:
{'id': 1, 'nombre': 'Alex'}

Consulta mediante gRPC:
id: 1, nombre: Alex


## 23. Funcionamiento general

El funcionamiento completo puede resumirse de la siguiente manera:

                    CLIENTE
                       │
             ┌─────────┴─────────┐
             │                   │
           REST                 gRPC
             │                   │
        HTTP + JSON        Protobuf + gRPC
             │                   │
             └─────────┬─────────┘
                       │
                       ▼
                 T01-SERVER
                       │
             ┌─────────┴─────────┐
             │                   │
          rest.py          grpc_server.py
             │                   │
             └─────────┬─────────┘
                       │
                       ▼
                 service.py
                       │
                       ▼
                  personas
                  (memoria)

La parte importante de la arquitectura es que:

REST ───────┐
            ├──→ buscar_por_id()
gRPC ───────┘

Ambos mecanismos terminan utilizando la misma lógica de negocio.

## 24. Dificultades encontradas durante el desarrollo

Durante la realización de la tarea se presentaron varios problemas que fueron solucionados antes de obtener la ejecución final.

Dificultad 1 — Imports de los archivos generados

Los archivos generados por gRPC no podían importarse directamente debido a la forma en que Python encontraba los módulos.

Solución

Se agregaron:

server/__init__.py
server/generated/__init__.py

y se configuró la ruta de server/generated mediante sys.path.

Dificultad 2 — Dependencia faltante en el cliente

El cliente produjo:

ModuleNotFoundError: No module named 'google'
Solución

Se identificó que los archivos generados por Protocol Buffers necesitaban la dependencia:

protobuf

Se agregó a:

client/requirements.txt

y se reconstruyó la imagen:

docker compose build client
Dificultad 3 — El cliente iniciaba antes de que el servidor estuviera listo

El cliente produjo:

Connection refused

porque intentaba realizar la consulta antes de que FastAPI y gRPC terminaran de iniciar.

Solución

Se agregó un healthcheck al servidor y:

condition: service_healthy

al depends_on del cliente.

Después de esto, Compose esperó a que el servidor estuviera saludable antes de iniciar el cliente.

Dificultad 4 — Comunicación entre contenedores

Al ejecutar el cliente dentro de Docker no se puede utilizar:

localhost

para referirse al servidor.

localhost dentro del cliente representa al propio contenedor del cliente.

Solución

Se utilizó la variable:

SERVIDOR=server

donde server corresponde al nombre del servicio definido en compose.yaml.

Docker Compose proporciona resolución de nombres dentro de la red:

t01_default

permitiendo que el cliente encuentre al servidor mediante:

server:8000
server:50051


## 25. Resultado final

Después de resolver los problemas anteriores, se obtuvo una ejecución correcta utilizando Docker Compose.

El servidor inició REST y gRPC:

Servidor REST iniciado en el puerto 8000
Servidor gRPC iniciado en el puerto 50051

Docker comprobó que el servidor estaba listo:

Container t01-server Healthy

El cliente pudo realizar ambas consultas:

Consulta mediante REST:
{'id': 1, 'nombre': 'Alex'}

Consulta mediante gRPC:
id: 1, nombre: Alex

Y terminó correctamente:

t01-client exited with code 0

## 26. Conclusión

Esta tarea permitió implementar un mismo servicio utilizando dos mecanismos diferentes de comunicación: REST y gRPC.

La principal característica del diseño fue mantener la lógica de negocio compartida, evitando implementar una búsqueda independiente para cada protocolo.

REST utiliza:

HTTP + JSON

mientras que gRPC utiliza:

gRPC + Protocol Buffers

La tarea también permitió trabajar con Docker, imágenes, contenedores, redes y Docker Compose.

Docker Compose permitió separar el cliente y el servidor en contenedores independientes y conectarlos mediante una red interna. Además, mediante healthcheck se pudo controlar que el cliente esperara a que el servidor estuviera listo.

Durante el desarrollo se encontraron problemas relacionados con dependencias, imports de los archivos generados por gRPC, comunicación entre contenedores y tiempos de inicio. Estos problemas fueron identificados mediante los mensajes de error y solucionados modificando la configuración y la estructura de la tarea.

Finalmente, se realizó una comparación del tamaño de los mensajes de aplicación. En la prueba realizada, REST produjo una respuesta de 24 bytes, mientras que los mensajes Protocol Buffers de gRPC ocuparon 10 bytes en total entre solicitud y respuesta. Esta medición se considera únicamente sobre el payload serializado y no sobre el tráfico completo de red.

Con esto, el proyecto demuestra cómo un mismo servicio puede ofrecer diferentes interfaces de comunicación manteniendo separada la lógica de negocio de los mecanismos utilizados para comunicarse con el cliente.