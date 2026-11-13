# Tarea 01 - Roman Salgado

## Arquitectura y Separación de Lógica

La lógica de negocio se centralizó en el archivo `logica.py`, el cual contiene la base de datos simulada y la función `consultar_datos()`. Esta función es totalmente agnóstica a la red.

* **Controlador REST (FastAPI):** Escucha peticiones HTTP, extrae el ID, invoca `consultar_datos()` y transforma el diccionario resultante a JSON.
* **Controlador gRPC:** Escucha tráfico binario HTTP/2, invoca exactamente la misma función `consultar_datos()` y empaqueta el resultado usando el formato definido en el contrato `servicio.proto`.

## Punto Extra: Medición de Bytes con tcpdump

Se capturó el tráfico de red de ambas peticiones dentro del contenedor cliente usando `tcpdump` interceptando la interfaz `eth0`. Los tamaños de los archivos `.pcap` generados fueron:

* **REST (`rest.pcap`):** 1376 bytes (12 paquetes)
* **gRPC (`grpc.pcap`):** 2201 bytes (18 paquetes)

**Explicación del resultado:**

A diferencia del ejemplo que vimos en clase (donde gRPC ganaba por ser más ligero), en mi prueba gRPC terminó pesando más que REST.

Esto pasó porque gRPC usa HTTP/2 por debajo, el cual necesita hacer un "saludo" inicial (handshake) mucho más complejo para establecer la conexión, lo que termina generando más paquetes al principio (18 paquetes en gRPC contra 12 en REST). 

Como la información que consulté era pequeñísima (prácticamente solo un string corto), todo ese "papeleo" de red para abrir la conexión de HTTP/2 pesó más que el beneficio y el ahorro que te da la compresión binaria de Protobuf. Por su parte, REST usa HTTP/1.1, que es más directo y necesitó menos paquetes de preparación para resolver una petición así de sencilla.
