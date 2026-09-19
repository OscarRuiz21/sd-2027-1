# T01 - El mismo servicio, por REST y por gRPC

## Descripción

Esta tarea implementa un mismo servicio utilizando dos formas de comunicación:

* REST mediante HTTP.
* gRPC mediante Protocol Buffers.

Ambos utilizan la misma lógica de negocio para consultar información de estudiantes.

## Arquitectura

El proyecto tiene un servidor y un cliente.

El servidor contiene:

* La lógica de negocio.
* Una API REST.
* Un servicio gRPC.

El cliente realiza la misma consulta utilizando REST y gRPC para poder comprobar que ambas interfaces proporcionan el mismo resultado.

La lógica de negocio está separada de los controladores REST y gRPC para evitar duplicar el código.

### Puertos

| Servicio | Puerto |
| -------- | ------ |
| REST     |   8000 |
| gRPC     |  50051 |

## Estructura del proyecto

```text
t01/
├── client/
│   ├── client.py
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── proto/
│   │   └── service.proto
│   └── generated/
│       ├── __init__.py
│       ├── service_pb2.py
│       └── service_pb2_grpc.py
│
├── server/
│   ├── app/
│   │   ├── __init__.py
│   │   ├── logic.py
│   │   ├── rest.py
│   │   ├── grpc_server.py
│   │   └── generated/
│   │       ├── __init__.py
│   │       ├── service_pb2.py
│   │       └── service_pb2_grpc.py
│   │
│   ├── proto/
│   │   └── service.proto
│   ├── Dockerfile
│   └── requirements.txt
│
├── docker-compose.yml
└── README.md
```

## Servicio

El servicio permite consultar un estudiante mediante su ID.

Por ejemplo, para el ID `2`, el servidor devuelve:

```text
ID: 2
Nombre: Liz
Edad: 22
```

### REST

La consulta REST utiliza:

```text
GET /students/{id}
```

Por ejemplo:

```text
GET /students/2
```

La respuesta es:

```json
{
  "id": 2,
  "name": "Liz",
  "age": 22
}
```

### gRPC

El servicio gRPC está definido en:

```text
proto/service.proto
```

El método utilizado es:

```text
GetStudent
```

El cliente envía un `StudentRequest` con el ID y recibe un `StudentResponse`.

## Ejecución con Docker

Para ejecutar el proyecto se necesita Docker y Docker Compose.

Desde la carpeta `t01` ejecutar:

```bash
docker compose up --build
```

El archivo `docker-compose.yml` crea dos servicios:

* `server`: ejecuta REST y gRPC.
* `client`: realiza las llamadas a ambos servicios.

El cliente espera a que REST y gRPC estén disponibles antes de realizar las consultas.

Para detener los contenedores:

```bash
docker compose down
```

## Evidencia de ejecución

Al ejecutar el proyecto correctamente, el cliente muestra una respuesta REST y una respuesta gRPC.

Ejemplo:

```text
REST está disponible.
gRPC está disponible.

========== REST ==========
Status: 200
Respuesta:
{'id': 2, 'name': 'Liz', 'age': 22}

========== gRPC ==========
ID: 2
Nombre: Liz
Edad: 22
Encontrado: True
```

Esto demuestra que el mismo servicio puede ser consumido mediante REST y gRPC y que ambos devuelven la información correspondiente al mismo estudiante.

## Archivos principales

### `server/app/logic.py`

Contiene la lógica de negocio y los datos de los estudiantes.

### `server/app/rest.py`

Contiene el endpoint REST:

```text
GET /students/{student_id}
```

### `server/app/grpc_server.py`

Implementa el servicio gRPC definido en el archivo `.proto`.

### `proto/service.proto`

Define el contrato del servicio gRPC, incluyendo:

* `StudentService`
* `GetStudent`
* `StudentRequest`
* `StudentResponse`

### `client/client.py`

Realiza una consulta utilizando REST y otra utilizando gRPC.

### Docker

El servidor y el cliente tienen sus propios `Dockerfile` y se ejecutan mediante Docker Compose en una red común.
