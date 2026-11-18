# Evidencia de Ejecución · Tarea T01

**Alumno:** Diego Adrián Contreras del Ángel - 320298835  
**Materia:** Sistemas Distribuidos 2027-1  
**Tarea:** T01 - El mismo servicio por REST y por gRPC  

---

## 1. Comando de Construcción y Ejecución

Se ejecutó el stack completo de contenedores mediante Docker Compose:

```bash
docker compose up --build
```

---

## 2. Salida Completa de la Terminal

```text
[+] Building 14.2s (25/25) FINISHED
 => [server internal] load build definition from Dockerfile
 => [client internal] load build definition from Dockerfile
 => [server internal] load .dockerignore
 => [client internal] load .dockerignore
 => [server 1/7] FROM docker.io/library/python:3.11-slim
 => [client 1/7] FROM docker.io/library/python:3.11-slim
 => [server 4/7] RUN pip install --no-cache-dir -r requirements.txt
 => [client 4/7] RUN pip install --no-cache-dir -r requirements.txt
 => [server 6/7] RUN python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. service.proto
 => [client 6/7] RUN python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. service.proto
 => [server 7/7] COPY service.py rest_app.py grpc_app.py main.py ./
 => [client 7/7] COPY client.py ./
 => naming to docker.io/library/t01-server:latest
 => naming to docker.io/library/t01-client:latest

[+] Running 3/3
 ✔ Network t01_red_t01        Created
 ✔ Container t01-server-1     Started
 ✔ Container t01-client-1     Started

Attaching to client-1, server-1
server-1  | INFO:     Started server process [1]
server-1  | INFO:     Waiting for application startup.
server-1  | INFO:     Application startup complete.
server-1  | INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
client-1  | Esperando a que el servidor esté listo...
server-1  | INFO:     172.24.0.3:39892 - "GET /users/1 HTTP/1.1" 200 OK
server-1  | INFO:     172.24.0.3:39900 - "GET /users/1 HTTP/1.1" 200 OK
server-1  | INFO:     172.24.0.3:39914 - "GET /users/99 HTTP/1.1" 404 Not Found

client-1  | === PROBANDO REST ===
client-1  | [REST] Status: 200 | Payload recibido: 60 bytes | Respuesta: {"id":1,"name":"Usuario Uno","email":"usuario1@example.com"}
client-1  | [REST] Status: 404 | Payload recibido: 34 bytes | Respuesta: {"detail":"Usuario no encontrado"}
client-1  | 
client-1  | === PROBANDO gRPC ===
client-1  | [gRPC] Status: OK | Payload respuesta: 39 bytes | Encontrado: True, Nombre: Usuario Uno
client-1  | [gRPC] Status: OK | Payload respuesta: 0 bytes | Encontrado: False, Nombre: 

client-1 exited with code 0
```

---

## 3. Análisis de Resultados

### ID Existente (`id = 1`)
- **REST:** Código HTTP `200 OK`, retornando el payload JSON `{"id":1,"name":"Usuario Uno","email":"usuario1@example.com"}` (60 bytes).
- **gRPC:** Llamada RPC exitosa (`Status: OK`), retornando un mensaje protobuf binario con `Encontrado: True, Nombre: Usuario Uno` (39 bytes). Se observa mayor eficiencia en tamaño de payload (39 bytes vs 60 bytes).

### ID Inexistente (`id = 99`)
- **REST:** El controlador devuelve un error semántico HTTP `404 Not Found` con cuerpo `{"detail":"Usuario no encontrado"}`.
- **gRPC:** La llamada RPC termina con éxito (`Status: OK` sin romper la comunicación a nivel de transporte), devolviendo el mensaje tipado con `Encontrado: False` y campos vacíos (0 bytes adicionales de datos).
