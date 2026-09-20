\# Tarea 01: El mismo servicio por REST y por gRPC

\*\*Alumna:\*\* Sierra García Mariana



\## 1. Diseño y Lógica Compartida

La lógica de negocio principal quedó aislada completamente en el archivo `logica.py`. Contiene un diccionario en memoria y una función `obtener\_datos(id)` que devuelve la información o un error, esta función no tiene dependencias de red.



\## 2. Diferencia entre los Controladores

Dentro de `servidor.py`, ambos controladores importan y consumen `logica.py`. 

\* El controlador REST (con Flask) recibe la petición por HTTP GET llama a la lógica y responde serializando en JSON.

\* El controlador gRPC recibe la petición binaria, llama a la misma lógica y responde usando el formato binario definido en `servicio.proto`.



Al compartir la lógica, el código no se duplica.



\## 3. Medición de Bytes 

Para la prueba con el ID 1 el tamaño del payload fue:

\* Tamaño por REST (JSON): 51 bytes

\* Tamaño por gRPC (Protobuf): 42 bytes



\*\*¿Por qué esta diferencia?\*\*

gRPC resulta más ligero porque viaja en un formato binario estructurado (Protocol Buffers). A diferencia de REST (que usa JSON) no necesita enviar llaves, comillas, ni los nombres de los atributos como texto en cada respuesta lo que ahorra ancho de banda.



\## 4. Instrucciones de ejecución

Para levantar los servidores y el cliente conectados ejecuta:

`docker-compose up --build`

