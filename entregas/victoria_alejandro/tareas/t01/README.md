# Tarea 1: Comparación REST vs gRPC

## Diseño de la solución
Para esta tarea se simula la búsqueda de productos de un supermercado.
La "base de datos" es un diccionario estático en el archivo `logica.py`. 

El archivo `servidor.py` trae la lógica y levanta dos servicios concurrentemente usando el módulo `threading` de Python:
- Un controlador REST construido con Flask en el puerto 8000.
- Un servidor gRPC usando `grpcio` en el puerto 50051.

Ambos controladores simplemente reciben la petición, extraen el ID, invocan `logica.obtener_producto(id)` y formatean la respuesta a JSON para REST, y a un objeto Protobuf para gRPC.

## Diseño de Contenedores
Para contenerizar la solución y mantener un entorno aislado, se diseñaron dos imágenes basadas en `python:3.10-slim` y se orquestaron con Docker Compose:

*   **Dockerfile.server:** Es una imagen ligera que instala las dependencias de Python que se van a usar con ayuda del archivo `requerimientos.txt`, copia el código fuente y compila en tiempo de construcción el archivo `.proto` usando `grpc_tools.protoc`. Su comando de inicio levanta automáticamente el script `servidor.py`.
*   **Dockerfile.client:** Utiliza la misma imagen base, pero añade un paso adicional mediante `apt-get` para instalar herramientas de red del sistema operativo (`tcpdump` y `procps`), necesarias para capturar el tráfico. En lugar de ejecutar el script y apagarse, su comando principal mantiene el contenedor vivo en segundo plano, permitiendo entrar y salir de la consola para usar el script interactivo múltiples veces.
*   **docker-compose.yml:** Define la arquitectura de red y levanta ambos servicios. Crea una red interna de Docker automáticamente donde el cliente puede comunicarse con el servidor usando el nombre del servicio `server` como host. Además, en el servicio del cliente se habilitaron las opciones `stdin_open: true` y `tty: true` para permitir la entrada interactiva de teclado cuando nos conectamos por consola.

## Cómo levantarlo
1. En la raíz del proyecto, ejecutar: `docker-compose up -d --build`
2. Para acceder al cliente interactivo, entrar al contenedor: `docker exec -it <nombre_del_contenedor_cliente> bash`
3. Ejecutar: `python cliente.py`

## Punto Extra: Medición de Bytes
Para medir la diferencia real en la red, se instaló `tcpdump` en el contenedor del cliente. Al capturar el tráfico con `tcpdump -i eth0 port 8000 or port 50051`, luego desde otra ventana de la terminal donde no está corriendo el contenedor ejecuté este comando para extraer la captura de tcpdump `docker cp t01_cliente:/app/captura.pcap .`. En Wireshark abrí el archivo y filtré el tráfico de los puertos 8000 y 50051, donde encontré la longitud en bytes de los datos transferidos por cada protocolo.

*   **Llamada REST:** 218 + 165 = 383 bytes
*   **Llamada gRPC:** 348 + 251 = 599 bytes

**Análisis de los resultados:**
A diferencia del ejemplo teórico visto en clase donde gRPC enviaba menos bits que REST, en esta medición real de una sola petición aislada, gRPC resultó ser más pesado. Esto se debe a que gRPC opera sobre HTTP/2. Al iniciar la comunicación, HTTP/2 requiere un intercambio inicial de tramas de control antes de enviar el payload binario de Protobuf. REST, al usar HTTP/1.1 con JSON, envía el texto directamente sin el handshake inicial de control tan robusto. Si la conexión se mantuviera abierta para miles de peticiones secuenciales, el peso inicial de gRPC se amortizaría y la compresión de Protobuf superaría a la de JSON.
