# Tarea 01: El mismo servicio por REST y por gRPC

Cortés Bolaños Angel David - 423066940

## Diseño y Lógica Compartida
El sistema se compone de un solo programa de servidor en Python (`server/main.py`) que expone una única lógica de negocio a través de dos interfaces distintas. 
*   **La Lógica:** Un diccionario en memoria (`CATALOGO`) y una función `buscar_item()` que recibe un ID y retorna los datos.
*   **Controlador REST:** Usa FastAPI en el puerto 3000. Llama a `buscar_item()` y empaqueta el retorno en un `JSONResponse` con sus respectivos códigos HTTP.
*   **Controlador gRPC:** Usa un hilo secundario en el puerto 50051. Llama a la **misma** función `buscar_item()` y traduce el retorno a la estructura binaria definida en `servicio.proto`.

## Cómo levantarlo
El proyecto está contenedorizado. Estando en esta carpeta, ejecuta:

docker compose up --build

El servidor se quedará escuchando y el contenedor del cliente se ejecutará automáticamente, mostrará las comparaciones en consola y se detendrá.

## Punto Extra: Comparación de Bytes
Se midió el peso del payload a nivel de capa de aplicación (peso del mensaje procesado) usando la función `len(response.content)` para REST y `.ByteSize()` de Protobuf para gRPC.

**Resultados obtenidos al consultar el ID 101:**
*   **REST (JSON):** 87 bytes.
*   **gRPC (Protobuf):** 49 bytes.

**Conclusión:** gRPC representó un ahorro de casi el 45% en el tamaño del mensaje para la misma información exacta. Esto ocurre porque JSON envía todas las llaves (nombres de variables, comillas, corchetes) en texto plano, mientras que Protobuf comprime los campos usando tags numéricos binarios predefinidos en el contrato `.proto`.