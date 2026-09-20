# Tarea 01: El mismo servicio por REST y por gRPC
**Alumno:** Alejandro Rodríguez Jaramillo - 320199866 

## 1. Ejecución
Para levantar el servidor y el cliente conectados en la misma red hay que ejecutar:
```bash
    docker compose run --rm client node client/client.js
```

Esto iniciará el servidor, ejecutará el cliente para realizar las peticiones a ambas interfaces y destruirá el contenedor del cliente al finalizar.

## 2. Lógica
1. **Lógica de Negocio:** La función `buscarMedicamento` interactúa con el diccionario en memoria. Solo recibe un ID y devuelve un objeto de JavaScript.
2. **Controlador REST en Puerto 8080:** Escucha peticiones HTTP mediante Express. Extrae el parámetro de la URL, invoca a `buscarMedicamento` y convierte la respuesta a formato JSON.
3. **Controlador gRPC en Puerto 9090:** Importa el contrato `servicio.proto`. Extrae el ID del mensaje RPC, invoca a `buscarMedicamento` y transforma el resultado a la estructura binaria de Protobuf.

## 3. Punto Extra
Se midió el peso de los datos a nivel de aplicación y del paquete completo a nivel de red al consultar el medicamento con ID 2, correspondiente a Loratadina con un precio de 45.

### Capa de Aplicación
* **REST, 65 bytes:** Medido en el cliente evaluando la longitud de la respuesta en formato de texto. El JSON es pesado porque transmite en texto plano todas las etiquetas como nombre o precio, junto con sus comillas y espacios.
* **gRPC, 26 bytes:** Calculado mediante las reglas de Protobuf. Su peso teórico es exacto sumando 3 bytes del ID, 12 bytes del nombre, 9 bytes del precio y 2 bytes del valor booleano. Aquí no viajan nombres de variables, solo valores ligados a un número de índice.

### Capa de Red, Paquetes TCP capturados
Mediante el comando `tcpdump` ejecutado dentro del servidor, se capturaron los tamaños totales de los datos transmitidos:
* **REST, 300 bytes:** Incluye los 65 bytes del JSON más todo el peso de las cabeceras de HTTP en texto plano y la información de control que requiere el protocolo TCP para funcionar.
* **gRPC, 156 bytes:** Incluye los 26 bytes de Protobuf empaquetados en un marco de HTTP/2, el cual comprime las cabeceras minimizando drásticamente su tamaño en la red.

**Conclusión:** gRPC reduce el peso de transmisión a casi la mitad en la capa de red y a una tercera parte en la capa de aplicación, validando su alta eficiencia para la comunicación interna entre microservicios.

## 4. Uso de Inteligencia Artificial
Para el punto extra, se utilizó **Gemini 3.1 Pro** como apoyo para identificar las herramientas de medición adecuadas en las distintas capas. 

**Prompt de apoyo utilizado:**
> "¿Cuáles son las opciones más precisas para medir el tamaño exacto en bytes de peticiones REST y gRPC en Node.js, tanto a nivel de aplicación (código) como a nivel de red (Docker)?"

---

## 5. Referencias
[1] OpenJS Foundation, "Node.js v20.x Documentation: Buffer," Node.js API Reference. En línea. Disponible en: https://nodejs.org/docs/latest-v20.x/api/buffer.html 
[2] Google, "Protocol Buffers Encoding," Protobuf Documentation. En línea. Disponible en: https://protobuf.dev/programming-guides/encoding/ 
[3] The Tcpdump Group, "tcpdump manual page," Tcpdump. En línea. Disponible en: https://www.tcpdump.org/manpages/tcpdump.1.html