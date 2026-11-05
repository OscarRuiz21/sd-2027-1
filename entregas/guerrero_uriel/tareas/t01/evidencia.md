# Evidencia de Ejecución — Tarea T01

**Alumno:** Guerrero López Uriel Iván  
**Materia:** Redes de Datos Seguras  

---

## 1. Compilación y Construcción de Contenedores

Se ejecutó el comando `docker compose up --build` para compilar las imágenes `t01-servidor` y `t01-cliente` desde sus respectivos `Dockerfile`, instalando las dependencias (`fastapi`, `uvicorn`, `grpcio`, `grpcio-tools`, `requests`) y generando el código de Protocol Buffers a partir de `service.proto`.

![Construcción de imágenes en Docker](<capturas_prueba/Imagen 1.png>)

---

## 2. Inicialización del Servidor Dual (REST y gRPC)

Una vez creados los contenedores y la red `t01_default`, el servidor inicia dos hilos independientes:
- **Servidor REST**: FastAPI/Uvicorn escuchando en el puerto `8000`.
- **Servidor gRPC**: Servicio `UsuarioServiceServicer` escuchando en el puerto `50051`.

![Arranque de los servidores y logs HTTP](<capturas_prueba/Imagen 2.png>)

---

## 3. Pruebas de Cliente y Medición de Payload

El contenedor cliente ejecuta las pruebas automáticamente contra ambos puertos del servidor:

![Salida completa del cliente](<capturas_prueba/Imagen 3.png>)

### Explicación de los Resultados:

1. **Consulta ID = 1 (Existe)**:
   - **REST (JSON)**: Devolvió código `200 OK` con un cuerpo de **76 Bytes**.
   - **gRPC (ProtoBuf)**: Devolvió la respuesta estructurada con un tamaño de **35 Bytes**.
   - **Conclusión**: gRPC reduce el tamaño del payload en un **53.9%** al serializar los datos en formato binario.

2. **Consulta ID = 99 (No existe)**:
   - **REST (JSON)**: Devolvió un error HTTP `404 Not Found` con detalle en JSON de **34 Bytes**.
   - **gRPC (ProtoBuf)**: Retornó la respuesta indicando `encontrado = False` con solo **2 Bytes**.
   - **Conclusión**: gRPC optimiza drásticamente los mensajes con campos por defecto o vacíos.