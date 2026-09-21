# T01: Servicio por REST y por gRPC

## 1. Descripción General
Este proyecto implementa un microservicio simple de consulta de datos expuesto simultáneamente a través de dos interfaces de red distintas: REST (HTTP/1.1 + JSON) y gRPC (HTTP/2 + Protobuf).

El objetivo  es garantizar que la lógica de negocio esté completamente desacoplada de la capa de transporte. Ambos protocolos conviven en el mismo proceso de servidor y consumen la misma fuente de datos sin duplicar código


## 2. Estructura del Repositorio

```text
t01/
├── docker-compose.yml       # Orquestación de la red y contenedores
├── README.md                # Documentación de diseño y ejecución
├── proto/
│   └── data.proto           # Contrato de interfaz gRPC
├── servidor/
│   ├── Dockerfile           # Imagen del servicio
│   ├── requirements.txt     # Dependencias de Python para el servidor
│   ├── db.py                # Datos persistentes en memoria
│   ├── server_rest.py       # Controlador y adaptador REST (Flask)
│   ├── server_grpc.py       # Controlador y adaptador gRPC
│   ├── data_pb2.py          # Código generado por protoc (mensajes)
│   ├── data_pb2_grpc.py     # Código generado por protoc (stubs)
│   └── main.py              # Punto de entrada (ejecución multihilo)
└── cliente/
    ├── Dockerfile           # Imagen del cliente de pruebas
    ├── requirements.txt     # Dependencias del cliente
    ├── client.py            # Cliente de pruebas para ambos protocolos
    ├── data_pb2.py          # Código generado por protoc (mensajes)
    └── data_pb2_grpc.py     # Código generado por protoc (stubs)
```
## 3. Diseño y arquitectura
La lógica de negocio reside en **`servidor/db.py`**.
* Contiene el diccionario en memoria (`GENIUS`) para almacenar los datos.
* Expone la función `get_data_by_id(requested_id)`.
* Define la estructura de respuesta (indicador de éxito, datos y mensaje de error).

**Aislamiento:** Ni el controlador REST ni el controlador gRPC tienen acceso directo a los datos. Ambos actúan como  intermediarios que delegan la consulta a `db.py`.

### REST vs gRPC
Aunque ambos realizan lo mismo, la manera en la que se comunican y exponen la información cambia:
*   **El Controlador REST (`server_rest.py`):** Utiliza el framework Flask. Escucha peticiones HTTP/1.1. Recibe el ID en la URL (`/data/<id>`), invoca a `db.get_data_by_id(id)` y formatea la respuesta como texto plano en JSON, devolviendo códigos de estado HTTP (`200 OK` o `404 Not Found`).

*   **El Controlador gRPC (`server_grpc.py`):** Utiliza la biblioteca `grpcio`. Utiliza el contrato estricto definido en `proto/data.proto`. Deserializa el mensaje binario HTTP/2 entrante, invoca a la misma función `db.get_data_by_id(id)` y devuelve un objeto binario `DataResponse`, donde los errores se manejan como un campo dentro del propio mensaje.

Para cumplir con un solo servidor, `servidor/main.py` inicializa el servidor gRPC en un hilo secundario y la aplicación Flask en el hilo principal. Ambos servicios se ejecutan dentro del mismo contenedor, compartiendo los mismos recursos.


## 4. Instrucciones para ejecutar el proyecto

1. Asegurarse de tener Docker instalado.
2. Desde la raíz del proyecto, ejecutar el siguiente comando:
    ```bash
    docker compose up --build
    ```
3. El contenedor del servidor se levantará exponiendo los puertos 8080 (REST) y 50051 (gRPC).
4. El contenedor del cliente se ejecutará a continuación, realizará las llamadas de prueba a ambos puertos de red y mostrará los resultados en consola.

## 5. Ejecución del proyecto
Al ejecutar el comando de construcción, la consola muestra la inicialización de los servicios y las pruebas del cliente (Se consulta un ID existente y uno inexistente por cada protocolo):

```text
Attaching to cliente-1, servidor-1
servidor-1  | *** Iniciando servicios (REST + gRPC) ***
servidor-1  |  * Serving Flask app 'server_rest'
servidor-1  |  * Debug mode: off
servidor-1  | WARNING: This is a development server. Do not use it in a production deployment. Use a production WSGI server instead.
servidor-1  |  * Running on all addresses (0.0.0.0)
servidor-1  |  * Running on http://127.0.0.1:8080
servidor-1  |  * Running on http://172.20.0.2:8080
servidor-1  | Press CTRL+C to quit
servidor-1  | 172.20.0.3 - - [21/Sep/2026 05:26:19] "GET /data/1 HTTP/1.1" 200 -
servidor-1  | 172.20.0.3 - - [21/Sep/2026 05:26:19] "GET /data/99 HTTP/1.1" 404 -
cliente-1   | Iniciando pruebas del cliente...
cliente-1   | 
cliente-1   | --- Probando REST (ID: 1) ---
cliente-1   | Código de Estado: 200
cliente-1   | Datos de JSON: {'error': '', 'id': '1', 'name': 'Ada Lovelace'}
cliente-1   | 
cliente-1   | --- Probando gRPC (ID: 1) ---
cliente-1   | Respuesta Protobuf recibida:
cliente-1   | ID: '1'
cliente-1   | Name: 'Ada Lovelace'
cliente-1   | Error: ''
cliente-1   | 
cliente-1   | --- Probando REST (ID: 99) ---
cliente-1   | Código de Estado: 404
cliente-1   | Datos de JSON: {'error': 'Data not found', 'id': '', 'name': ''}
cliente-1   | 
cliente-1   | --- Probando gRPC (ID: 99) ---
cliente-1   | Respuesta Protobuf recibida:
cliente-1   | ID: ''
cliente-1   | Name: ''
cliente-1   | Error: 'Data not found'
cliente-1   | 
cliente-1   | Pruebas finalizadas.
cliente-1 exited with code 0
```
