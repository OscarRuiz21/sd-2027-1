T01 · El mismo servicio, por REST y por gRPC

Alumno: Guerrero López Uriel Iván

Número de Cuenta: 320046045

Fecha de Entrega: 20 Septiembre 2026

1. Introducción y Propósito

El objetivo de este proyecto es implementar un servicio distribuido básico utilizando dos paradigmas de comunicación distintos: REST (sobre HTTP/1.1 con formato JSON) y gRPC (sobre HTTP/2 con Protocol Buffers).

El requisito fundamental de esta práctica es garantizar el rehuso absoluto de la lógica de negocio:

Existe una sola fuente de verdad para los datos y las reglas de consulta (service.py).

Las interfaces REST (FastAPI) y gRPC (grpcio) actúan únicamente como capas de transporte y serialización (controladores/adaptadores), canalizando todas las peticiones hacia la misma función central de consulta.

2. Arquitectura del Sistema

                        +----------------------------+
                        |         Cliente            |
                        |        (client.py)         |
                        +--------------+-------------+
                                       |
                   +-------------------+-------------------+
                   | HTTP GET                              | gRPC (HTTP/2)
                   v                                       v
        +----------------------+                +----------------------+
        |   Controlador REST   |                |   Controlador gRPC   |
        |      (FastAPI)       |                |     (grpcio)         |
        +----------+-----------+                +----------+-----------+
                   |                                       |
                   +-------------------+-------------------+
                                       |
                                       v
                        +----------------------------+
                        |  Lógica Compartida / BD   |
                        |        (service.py)        |
                        +----------------------------+


Componentes Principales:

service.proto: Contrato IDL de gRPC que define las estructuras de mensajes (UsuarioRequest, UsuarioResponse) y el servicio (UsuarioService).

service.py: Lógica de negocio neutra e independiente de la red. Contiene un diccionario en memoria simulando la base de datos y la función buscar_usuario_por_id().

server.py: Servidor concurrente que inicializa simultáneamente:

Servidor gRPC escuchando en el puerto 50051.

Servidor REST (Uvicorn / FastAPI) escuchando en el puerto 8000.

client.py: Cliente automatizado de pruebas que consulta datos existentes e inexistentes mediante ambas interfaces.

docker-compose.yml: Orquestador que despliega el servidor y el cliente en una red de contenedores aislada.

3. Instrucciones de Despliegue

Compilar e inicializar la infraestructura contenerizada:

docker compose up --build


Para detener los servicios al finalizar las pruebas:

docker compose down

## 3. Comparación y Medición de Bytes (Punto Extra)

### Mediciones Obtenidas

| Métrica | REST (JSON) | gRPC (Protocol Buffers) | Diferencia |

| :--- | :--- | :--- | :--- |

| **Protocolo de Red** | HTTP/1.1 | HTTP/2 | HTTP/2 permite multiplexación |
| **Formato de Payload** | Texto Plano (JSON) | Binario (ProtoBuf) | ProtoBuf omite nombres de claves |
| **Payload ID 1 (Existe)** | **76 Bytes** | **35 Bytes** | **gRPC es 53.9% más pequeño** |
| **Payload ID 99 (No existe)** | **34 Bytes** | **2 Bytes** | **gRPC es 94.1% más pequeño** |

### Explicación Técnica

1. **JSON vs Protocol Buffers**: JSON envía cada nombre de clave en texto plano (`"nombre"`, `"email"`, `"encontrado"`). Protocol Buffers asigna etiquetas numéricas a los campos (`id = 1`, `nombre = 2`), transmitiendo únicamente el número de campo y el valor binario.

2. **Campos Opcionales/Vacíos**: Cuando el usuario no existe (`id=99`), gRPC envía solo `2 bytes` representando la estructura vacía, mientras que REST envía la respuesta completa en JSON `{ "detail": "Usuario no encontrado" }`.