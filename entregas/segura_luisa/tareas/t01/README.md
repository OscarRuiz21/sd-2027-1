\# T01 · El mismo servicio por REST y por gRPC



\## 1. Diseño de la Arquitectura

El sistema implementa una arquitectura desacoplada donde una única fuente de verdad (`server/service.py`) encapsula la lógica de negocio y los datos en memoria. Sobre esta lógica se montan dos interfaces de transporte independientes en un único servidor:

\- \*\*Controlador REST:\*\* Expuesto con FastAPI sobre HTTP/1.1 en el puerto `8000`, serializando respuestas en texto plano JSON.

\- \*\*Controlador gRPC:\*\* Expuesto con gRPC sobre HTTP/2 en el puerto `50051`, serializando los mensajes en formato binario mediante Protocol Buffers (`servicio.proto`).



Ambos controladores invocan la misma función interna `consultar\_item(id)`.



\## 2. Diferencias entre controladores

| Característica | REST | gRPC |

|---|---|---|

| \*\*Protocolo base\*\* | HTTP/1.1 | HTTP/2 (multiplexado) |

| \*\*Serialización\*\* | JSON (texto legible) | Protocol Buffers (binario compacto) |

| \*\*Contrato\*\* | Implícito (OpenAPI/docs) | Estricto y tipado (`servicio.proto`) |

| \*\*Manejo de errores\*\* | Códigos de estado HTTP (404 Not Found) | Códigos gRPC canónicos (`StatusCode.NOT\_FOUND`) |



\## 3. Punto Extra: Medición de Bytes

Se midió el payload transmitido en la consulta exitosa del ID `1`:

\- \*\*REST (JSON):\*\* 86 bytes en cuerpo + \~155 bytes en encabezados de texto plano = \~241 bytes totales.

\- \*\*gRPC (Protobuf):\*\* 42 bytes en el payload binario serializado.



\*\*Explicación:\*\*  

En REST, JSON repite los nombres de cada clave (`"nombre"`, `"detalle"`, `"encontrado"`) en cada mensaje y envía texto ASCII. En contraste, Protobuf utiliza etiquetas numéricas compactas (varints de 1 o 2 bytes) en lugar de cadenas repetidas para mapear los campos, logrando una reducción de más del 50% en el tamaño de los datos transmitidos.



\## 4. Instrucciones para levantar el proyecto

Desde la carpeta `entregas/segura\_luisa/tareas/t01`:

```bash

docker compose up --build

