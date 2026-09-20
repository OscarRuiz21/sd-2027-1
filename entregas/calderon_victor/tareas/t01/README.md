# T01 · El mismo servicio, por REST y por gRPC

**Nombre:** Victor Emiliano Calderón Gutiérrez  

---

## 1. Contexto y Arquitectura del Servicio

En las sesiones teóricas de la clase contrastamos REST y gRPC en el pizarrón. El objetivo principal de esta tarea es llevar esa comparación a la práctica mediante la implementación de **un solo servicio con una única lógica de negocio**, expuesto simultáneamente bajo dos protocolos e interfaces de comunicación diferentes:

1. **REST (HTTP/1.1 + JSON)**: En el puerto `8080`, atendiendo solicitudes `GET /books/{id}`.
2. **gRPC (HTTP/2 + Protocol Buffers binario)**: En el puerto `50051`, implementando el método RPC `BookService/GetBook`.

```
                        ┌───────────────────────────────────────────────────────────┐
                        │              Proceso Único (Spring Boot 3.3.x)             │
                        │                                                           │
   [Cliente HTTP/REST] ──► [BookRestController (:8080)]                              │
   (HTTP/1.1 + JSON)    │         │                                                 │
                        │         ▼                                                 │
                        │    ┌──────────────────────────────────────────────┐       │
                        │    │      BookRepository (Single Source of Truth)  │       │
                        │    │      - Catálogo técnico en memoria (O'Reilly)│       │
                        │    │      - Validación de IDs y reglas de negocio │       │
                        │    └──────────────────────────────────────────────┘       │
                        │         ▲                                                 │
   [Cliente gRPC/Stub] ──► [BookGrpcService (:50051)]                                │
   (HTTP/2 + Protobuf)  │                                                           │
                        └───────────────────────────────────────────────────────────┘
```

### 1.1. ¿Dónde reside la lógica compartida? (Single Source of Truth)

Tal como lo marca la regla central del curso, la lógica no debe duplicarse ni separarse en aplicaciones independientes. Toda la lógica de negocio y los datos residen en el componente de dominio [`BookRepository.java`](src/main/java/com/distribuidos/domain/BookRepository.java), anotado con `@Repository`.

- **Almacenamiento en memoria:** Administra un catálogo con libros técnicos de sistemas distribuidos y redes de O'Reilly (`ddia`, `k8s`, `hpbn`, `sre`) utilizando un `Map` concurrente.
- **Validación y recuperación:** El método `findById(String id)` centraliza la validación de los identificadores (control de cadenas vacías/nulas, sanitización con `trim()` y normalización de minúsculas) y devuelve un `Optional<Book>` representando la entidad inmutable [`Book.java`](src/main/java/com/distribuidos/domain/Book.java).

### 1.2. Contraste entre controladores: REST vs gRPC

Lo único que cambia entre una interfaz y otra es la capa de transporte, serialización y control de errores:

| Aspecto | Controlador REST ([`BookRestController.java`](src/main/java/com/distribuidos/rest/BookRestController.java)) | Servicio gRPC ([`BookGrpcService.java`](src/main/java/com/distribuidos/grpc/BookGrpcService.java)) |
| :--- | :--- | :--- |
| **Framework / Anotación** | Spring Web MVC (`@RestController`, `@GetMapping`) | Starter de net.devh (`@GrpcService`) heredando de `BookServiceImplBase` |
| **Protocolo de Red** | HTTP/1.1 (texto/flujo estándar) | HTTP/2 (multiplexado binario sobre TCP) |
| **Serialización** | Texto JSON (`application/json`) con Jackson | Binario Protocol Buffers (`proto3`) |
| **Puerto Expuesto** | `8080` | `50051` |
| **Caso Exitoso** | Retorna `200 OK` con JSON del libro | Retorna mensaje `BookResponse` vía `responseObserver.onNext()` y `onCompleted()` |
| **Caso Recurso Inexistente** | Retorna `404 Not Found` con cuerpo `{"detail": "Libro no encontrado"}` | Lanza `Status.NOT_FOUND.withDescription(...)` a través de `responseObserver.onError()` |
| **Dependencia Inyectada** | Inyecta el bean singleton `BookRepository` | Inyecta exactamente el **mismo** bean singleton `BookRepository` |

---

## 2. Contrato de Servicio: `.proto`

El contrato binario entre cliente y servidor se encuentra formalmente definido en [`src/main/proto/book.proto`](src/main/proto/book.proto):

```protobuf
syntax = "proto3";

package com.distribuidos;

option java_multiple_files = true;
option java_package = "com.distribuidos.grpc";
option java_outer_classname = "BookProto";

// Servicio gRPC para consulta de catálogo de libros técnicos
service BookService {
  rpc GetBook (BookRequest) returns (BookResponse);
}

// Mensaje de solicitud con el identificador del libro
message BookRequest {
  string id = 1;
}

// Mensaje de respuesta con los metadatos y contenido del libro
message BookResponse {
  string id = 1;
  string title = 2;
  string author = 3;
  int32 pages = 4;
  int32 year = 5;
  string summary = 6;
}
```

A través de `protobuf-maven-plugin` (0.6.1) y el compilador de artefactos `protoc-gen-grpc-java` (1.64.0), este contrato genera automáticamente el stub bloqueante de cliente y la clase base abstracta de servicio `BookServiceGrpc.BookServiceImplBase`.

---

## 3. Cliente Automatizado de Pruebas y Benchmark

El cliente automatizado vive en [`src/main/java/com/distribuidos/client/BenchmarkClient.java`](src/main/java/com/distribuidos/client/BenchmarkClient.java). Se diseñó para ejecutarse de manera autónoma al arrancar el contenedor y realiza las siguientes validaciones:

1. **Llamada exitosa por REST:** Consulta `GET /books/ddia` mediante `java.net.http.HttpClient`, verifica el código HTTP `200`, valida el contenido JSON y extrae la longitud exacta en bytes de la respuesta.
2. **Llamada exitosa por gRPC:** Consulta `BookService/GetBook` con ID `"ddia"` utilizando el blocking stub `BookServiceGrpc`, valida los campos devueltos y obtiene el tamaño binario en bytes mediante `response.getSerializedSize()`.
3. **Llamada fallida por REST:** Consulta un ID inexistente (`libro_inexistente_xyz`), verificando que el servidor responda con código HTTP `404` y el mensaje de detalle esperado `{"detail":"Libro no encontrado"}`.
4. **Llamada fallida por gRPC:** Consulta el mismo ID inexistente y captura la excepción `StatusRuntimeException`, verificando que el código devuelto sea `Status.Code.NOT_FOUND`.
5. **Benchmark del Punto Extra:** Ejecuta la comparativa en todo el catálogo técnico y genera la tabla de diferencias de bytes y porcentaje de ahorro.

---

## 4. Contenerización y Orquestación con Docker Compose

Para garantizar que los entornos sean reproducibles, eficientes y ligeros, se implementaron construcciones multi-etapa (multi-stage builds):

### 4.1. `Dockerfile.server`
- **Etapa de construcción (`builder`):** Utiliza `maven:3.9-eclipse-temurin-21` para descargar dependencias, compilar los archivos `.proto` y empaquetar el JAR ejecutable de Spring Boot (`t01-rest-grpc-1.0.0.jar`).
- **Etapa de producción (`runtime`):** Utiliza `eclipse-temurin:21-jre` como imagen base delgada, copia exclusivamente el artefacto `.jar` final y expone los puertos `8080` y `50051`.

### 4.2. `Dockerfile.client`
- **Etapa de construcción (`builder`):** Compila las clases Java del cliente y copia las dependencias a `/app/target/dependency/`.
- **Etapa de producción (`runtime`):** Imagen limpia con `eclipse-temurin:21-jre` que corre directamente la clase `com.distribuidos.client.BenchmarkClient`.

### 4.3. Red y Sincronización en `docker-compose.yml`
El archivo [`docker-compose.yml`](docker-compose.yml) conecta ambos contenedores a la red compartida tipo puente `t01-network`. 

Para resolver el problema común donde el cliente inicia antes de que Spring Boot termine de arrancar, se configuró un **Healthcheck** en el servidor contra el endpoint `/actuator/health`. El cliente define `depends_on: server: condition: service_healthy`, asegurando que el cliente no inicie sus pruebas hasta que el servidor esté 100% operativo.

---

## 5. Instrucciones de Ejecución

Para construir y levantar todo el sistema de forma automatizada:

```powershell
# 1. Posicionarse en la carpeta de la tarea
cd entregas/calderon_victor/tareas/t01

# 2. Levantar los servicios y ejecutar la suite de pruebas
docker compose up --build
```

Si deseas realizar consultas manuales desde tu máquina host mientras el servidor está activo:

```powershell
# Consulta REST exitosa
curl http://localhost:8080/books/ddia

# Consulta REST fallida (404)
curl -i http://localhost:8080/books/no_existe
```

Para detener y limpiar los contenedores y la red creada:

```powershell
docker compose down
```

---

## 6. Evidencia de Ejecución

A continuación se presenta la salida real capturada durante la ejecución de los contenedores mediante `docker compose up`:

```text
[+] Running 3/3
 ✔ Network t01-network      Created                                                                             0.0s 
 ✔ Container t01-server     Healthy                                                                            10.5s 
 ✔ Container t01-client     Started                                                                            10.6s 
Attaching to t01-client, t01-server

t01-server  | 2026-09-20 21:53:07.135 [main] INFO  c.distribuidos.Application - Starting Application v1.0.0 using Java 21.0.8 with PID 1
t01-server  | 2026-09-20 21:53:07.820 [main] INFO  c.distribuidos.domain.BookRepository - BookRepository inicializado exitosamente con 4 libros en memoria.
t01-server  | 2026-09-20 21:53:08.520 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8080 (http) with context path '/'
t01-server  | 2026-09-20 21:53:08.610 [main] INFO  n.d.b.g.s.s.GrpcServerLifecycle - gRPC Server started, listening on address: *, port: 50051
t01-server  | 2026-09-20 21:53:08.615 [main] INFO  c.distribuidos.Application - ==================================================================
t01-server  | 2026-09-20 21:53:08.615 [main] INFO  c.distribuidos.Application -  Servidor T01 inicializado exitosamente:
t01-server  | 2026-09-20 21:53:08.615 [main] INFO  c.distribuidos.Application -    - REST endpoint: http://localhost:8080/books/{id}
t01-server  | 2026-09-20 21:53:08.615 [main] INFO  c.distribuidos.Application -    - gRPC service : localhost:50051 (BookService/GetBook)
t01-server  | 2026-09-20 21:53:08.615 [main] INFO  c.distribuidos.Application - ==================================================================

t01-client  | ================================================================================
t01-client  |    BENCHMARK & CLIENTE DE VALIDACIÓN: REST vs gRPC
t01-client  |    Materia: Sistemas Distribuidos | Tarea T01
t01-client  |    Autor: Victor Emiliano Calderón Gutiérrez
t01-client  |    Objetivo: server (REST :8080 | gRPC :50051)
t01-client  | ================================================================================
t01-client  | 
t01-client  | --- [TEST 1: REST ÉXITO - ID: ddia] ---
t01-server  | 2026-09-20 21:53:13.980 [http-nio-8080-exec-1] INFO  c.d.rest.BookRestController - [REST] Solicitud recibida: GET /books/ddia
t01-server  | 2026-09-20 21:53:13.981 [http-nio-8080-exec-1] INFO  c.distribuidos.domain.BookRepository - Libro encontrado con ID 'ddia': Designing Data-Intensive Applications
t01-server  | 2026-09-20 21:53:13.981 [http-nio-8080-exec-1] INFO  c.d.rest.BookRestController - [REST] Retornando libro: Designing Data-Intensive Applications
t01-client  |   URL Solicitada      : http://server:8080/books/ddia
t01-client  |   Código HTTP         : 200
t01-client  |   Cuerpo JSON Recibido: {"id":"ddia","title":"Designing Data-Intensive Applications","author":"Martin Kleppmann","pages":616,"year":2017,"summary":"The definitive guide to the architecture, storage engines, distributed consensus, and fault tolerance in modern data systems."}
t01-client  |   Bytes reales de payload: 251 bytes
t01-client  |   Estado: PASS [200 OK]
t01-client  | 
t01-client  | --- [TEST 2: gRPC ÉXITO - ID: ddia] ---
t01-server  | 2026-09-20 21:53:14.075 [grpc-default-executor-0] INFO  c.distribuidos.grpc.BookGrpcService - [gRPC] Solicitud recibida: BookService/GetBook con ID 'ddia'
t01-server  | 2026-09-20 21:53:14.075 [grpc-default-executor-0] INFO  c.distribuidos.domain.BookRepository - Libro encontrado con ID 'ddia': Designing Data-Intensive Applications
t01-server  | 2026-09-20 21:53:14.075 [grpc-default-executor-0] INFO  c.distribuidos.grpc.BookGrpcService - [gRPC] Retornando libro: 'Designing Data-Intensive Applications' [Tamaño Protobuf: 196 bytes]
t01-client  |   Método gRPC         : BookService/GetBook
t01-client  |   Respuesta Protobuf  : {
t01-client  | id: "ddia"
t01-client  | title: "Designing Data-Intensive Applications"
t01-client  | author: "Martin Kleppmann"
t01-client  | pages: 616
t01-client  | year: 2017
t01-client  | summary: "The definitive guide to the architecture, storage engines, distributed consensus, and fault tolerance in modern data systems."
t01-client  |   }
t01-client  |   Bytes reales Protobuf: 196 bytes
t01-client  |   Estado: PASS [OK]
t01-client  | 
t01-client  | --- [TEST 3: REST RECURSO NO ENCONTRADO - ID: libro_inexistente_xyz] ---
t01-server  | 2026-09-20 21:53:14.087 [http-nio-8080-exec-3] INFO  c.d.rest.BookRestController - [REST] Solicitud recibida: GET /books/libro_inexistente_xyz
t01-server  | 2026-09-20 21:53:14.087 [http-nio-8080-exec-3] WARN  c.distribuidos.domain.BookRepository - Libro con ID 'libro_inexistente_xyz' no fue encontrado en el catálogo.
t01-server  | 2026-09-20 21:53:14.087 [http-nio-8080-exec-3] WARN  c.d.rest.BookRestController - [REST] Libro no encontrado para ID: libro_inexistente_xyz
t01-client  |   URL Solicitada      : http://server:8080/books/libro_inexistente_xyz
t01-client  |   Código HTTP         : 404
t01-client  |   Cuerpo JSON Recibido: {"detail":"Libro no encontrado"}
t01-client  |   Estado: PASS [404 Not Found esperado con detalle]
t01-client  | 
t01-client  | --- [TEST 4: gRPC RECURSO NO ENCONTRADO - ID: libro_inexistente_xyz] ---
t01-server  | 2026-09-20 21:53:14.090 [grpc-default-executor-0] INFO  c.distribuidos.grpc.BookGrpcService - [gRPC] Solicitud recibida: BookService/GetBook con ID 'libro_inexistente_xyz'
t01-server  | 2026-09-20 21:53:14.090 [grpc-default-executor-0] WARN  c.distribuidos.domain.BookRepository - Libro con ID 'libro_inexistente_xyz' no fue encontrado en el catálogo.
t01-server  | 2026-09-20 21:53:14.090 [grpc-default-executor-0] WARN  c.distribuidos.grpc.BookGrpcService - [gRPC] Libro no encontrado para ID 'libro_inexistente_xyz'. Emite Status.NOT_FOUND
t01-client  |   Código gRPC Capturado: NOT_FOUND
t01-client  |   Descripción del Error: Libro no encontrado: libro_inexistente_xyz
t01-client  |   Estado: PASS [Status.NOT_FOUND capturado correctamente]
t01-client  | 
t01-client  | ================================================================================
t01-client  |    [PUNTO EXTRA] MEDICIÓN DE BYTES EN EL CUERPO: REST (JSON) vs gRPC (Protobuf)
t01-client  | ================================================================================
t01-client  | ID         | Título                                   | REST (Bytes) | gRPC (Bytes) | Ahorro (%)
t01-client  | --------------------------------------------------------------------------------
t01-client  | ddia       | Designing Data-Intensive Applications    |          251 |          196 |     21.91%
t01-client  | k8s        | Kubernetes: Up and Running               |          266 |          211 |     20.68%
t01-client  | hpbn       | High Performance Browser Networking      |          249 |          195 |     21.69%
t01-client  | sre        | Site Reliability Engineering             |          295 |          241 |     18.31%
t01-client  | --------------------------------------------------------------------------------
t01-client  | TOTAL      | Acumulado (4 llamadas exitosas)          |         1061 |          843 |     20.55%
t01-client  | ================================================================================
t01-client  | 
t01-client  | [RESULTADO GLOBAL] >>> TODAS LAS PRUEBAS PASARON EXITOSAMENTE (REST & gRPC) <<<
t01-client exited with code 0
```

---

## 7. Punto Extra: Reporte y Análisis de Medición de Bytes

### 7.1. Resultados Obtenidos

En las mediciones empíricas realizadas sobre los cuerpos de las respuestas, se obtuvieron los siguientes valores:

| ID del Libro | Título | REST Payload (JSON) | gRPC Payload (Protobuf) | Ahorro Neto | % Reducción |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `ddia` | Designing Data-Intensive Applications | 251 bytes | 196 bytes | 55 bytes | **21.91%** |
| `k8s` | Kubernetes: Up and Running | 266 bytes | 211 bytes | 55 bytes | **20.68%** |
| `hpbn` | High Performance Browser Networking | 249 bytes | 195 bytes | 54 bytes | **21.69%** |
| `sre` | Site Reliability Engineering | 295 bytes | 241 bytes | 54 bytes | **18.31%** |
| **Total** | **4 Consultas Acumuladas** | **1,061 bytes** | **843 bytes** | **218 bytes** | **20.55%** |

### 7.2. Metodología de Medición

Para asegurar que la comparación sea justa y científica, se midió estrictamente el tamaño de la carga útil (payload body) transmitida:
- **En REST:** Se extrajo el cuerpo crudo de la respuesta HTTP devuelto por el servidor en formato String UTF-8 (`response.body().getBytes(StandardCharsets.UTF_8).length`). Esto refleja el total de caracteres transferidos correspondientes a la estructura JSON.
- **En gRPC:** Se utilizó el método estándar `getSerializedSize()` provisto por el mensaje `BookResponse` generado por Protobuf. Este método calcula exactamente la cantidad de bytes que componen la trama binaria serializada que viaja encapsulada en el frame DATA de HTTP/2.

### 7.3. Explicación del porqué de la diferencia: ¿Por qué Protobuf es más pequeño?

En la clase vimos un ejemplo didáctico simplificado donde una estructura teórica pasaba de 180 bits a 60 bits (una reducción de casi el 66%). En nuestras mediciones reales observamos una reducción promedio constante del **20.55%** (~55 bytes ahorrados por registro). 

Esta diferencia y su magnitud se explican por los siguientes factores técnicos:

1. **Eliminación del overhead sintáctico de nombres de clave:**
   En cada respuesta JSON, se transmiten textualmente todas las claves del esquema:
   `"id":` (5 bytes), `"title":` (8 bytes), `"author":` (9 bytes), `"pages":` (8 bytes), `"year":` (7 bytes), `"summary":` (10 bytes), más las llaves `{}` y comas `,`. En total, el overhead de sintaxis en JSON representa **alrededor de 55 bytes en cada registro**.
   En gRPC/Protobuf, estos nombres de campos **no viajan por la red**. En su lugar, Protobuf asigna un *tag* binario compacto que combina el número de campo (1 a 6) y el tipo de alambre (*wire type*) en un solo byte.

2. **Codificación de tipos numéricos (Varints):**
   En JSON, un entero como `2017` o `616` se codifica como una secuencia de caracteres ASCII (4 bytes para `"2017"`, 3 bytes para `"616"`). En Protobuf, los enteros de tipo `int32` se codifican mediante Varints, ocupando típicamente 2 bytes.

3. **¿Por qué el ahorro fue del ~21% y no del 66%?**
   En este servicio, los objetos contienen cadenas de texto largas correspondientes al campo `summary` y `title` (por ejemplo, descripciones de más de 120 caracteres en inglés). Tanto en JSON como en Protobuf, el texto UTF-8 de las cadenas no se comprime a nivel de payload; cada caracter ocupa 1 byte.
   Por lo tanto, dado que la mayor parte del tamaño total del mensaje (~150 a 200 bytes) corresponde al texto propio de los datos, el ahorro del 21% proviene precisamente de haber eliminado todo el envoltorio estructural de JSON (los ~55 bytes de etiquetas y delimitadores). En mensajes donde predominan números, flags booleanos o IDs cortos, el porcentaje de ahorro de Protobuf supera con facilidad el 60% a 70%.

4. **Ventajas a nivel de transporte (HTTP/2 vs HTTP/1.1):**
   Adicional al cuerpo del mensaje, gRPC opera sobre HTTP/2, lo que permite compresión de encabezados con **HPACK** y multiplexación de múltiples solicitudes sobre una sola conexión TCP persistente, eliminando el costo de handshake y cabeceras redundantes que HTTP/1.1 arrastra en cada petición REST.
