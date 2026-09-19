# Tarea T01: Mismo servicio por REST y gRPC

## Diseño de Arquitectura
La lógica está en el server/service.py dentro de la función find_user_by_id(), la cual hace una consulta un diccionario en memoria. 
- Controlador REST (rest_app.py): Expone un endpoint HTTP/JSON sobre el puerto 8000.
- ontrolador gRPC (grpc_app.py): Hace que el servant generado por Protobuf en el puerto 50051.

## Instrucciones para ejecutar
```bash
docker compose up --build