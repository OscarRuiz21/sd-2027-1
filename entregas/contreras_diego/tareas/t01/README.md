# Tarea T01: Mismo servicio por REST y gRPC

**Alumno:** Diego Adrián Contreras del Ángel - 320298835

## Diseño de Arquitectura

El objetivo de esta tarea es implementar un mismo servicio de consulta de usuarios exponiéndolo a través de dos paradigmas de comunicación distintos: **REST (HTTP/JSON)** y **gRPC (HTTP/2 / Protobuf)**, demostrando cómo ambos coexisten desacoplados de la lógica de negocio central.

### 1. Centralización de la Lógica
La lógica de negocio reside únicamente en `server/service.py`, dentro de la función `find_user_by_id(user_id: int)`. Dicha función consulta la base de datos en memoria `USERS_DB` (con datos ficticios de prueba). Ambos controladores importan y consumen esta misma función, evitando la duplicación de código de dominio.

### 2. Diferencias entre Controladores (Manejo de ID inexistente)
- **Controlador REST (`server/rest_app.py`):**
  - Expone el endpoint `GET /users/{user_id}` en el puerto `8000`.
  - **Comportamiento ante ID existente:** Devuelve código de estado HTTP `200 OK` con el objeto JSON del usuario.
  - **Comportamiento ante ID inexistente:** Lanza una excepción `HTTPException(status_code=404, detail="Usuario no encontrado")`. En la arquitectura REST, la ausencia del recurso se mapea directamente al código de estado de transporte HTTP `404 Not Found`.

- **Controlador gRPC (`server/grpc_app.py`):**
  - Implementa el servicio definido en `service.proto` (`UserServiceServicer`) sobre el puerto `50051`.
  - **Comportamiento ante ID existente:** Retorna un mensaje `UserResponse(found=True, id=..., name=..., email=...)`.
  - **Comportamiento ante ID inexistente:** En lugar de lanzar una excepción de canal RPC, el contrato protobuf define un campo booleano explícito `bool found`. El servidor retorna exitosamente (`Status: OK` a nivel RPC) con `UserResponse(found=False)`. Esto demuestra el uso de contratos fuertemente tipados donde el estado de existencia del recurso viaja en el payload binario.

## Instrucciones para ejecutar

Para levantar tanto el servidor como el cliente de prueba automáticamente en contenedores Docker:

```bash
docker compose up --build
```

Para detener y limpiar los contenedores:

```bash
docker compose down
```
