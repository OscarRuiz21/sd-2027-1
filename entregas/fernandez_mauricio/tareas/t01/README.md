\# T01 - El mismo servicio por REST y por gRPC

Asignada en la S4 (12-sep) · entrega el domingo 20 de septiembre, 23:59

Alumno: Fernández Herrera Mauricio - sistemas distribuidos - Grupo 02



\## Descripción



Esta práctica implementa un mismo servicio de consulta de usuarios utilizando dos interfaces de comunicación: REST y gRPC.



La lógica de negocio se encuentra centralizada en `server/service.py`, mientras que el servidor expone dicha lógica mediante REST y gRPC. El cliente puede realizar la misma consulta utilizando cualquiera de los dos protocolos.



La aplicación se encuentra dockerizada y utiliza Docker Compose para ejecutar el cliente y el servidor dentro de una misma red.



\## Estructura del proyecto



```text

t01/

├── server/

│   ├── app.py

│   ├── service.py

│   ├── grpc\_server.py

│   ├── server.py

│   ├── requirements.txt

│   ├── Dockerfile

│   └── proto/

│       ├── service.proto

│       ├── service\_pb2.py

│       └── service\_pb2\_grpc.py

│

├── client/

│   ├── client.py

│   ├── requirements.txt

│   ├── Dockerfile

│   ├── service.proto

│   ├── service\_pb2.py

│   └── service\_pb2\_grpc.py

│

├── evidencias/

├── evidencia.md

├── medir\_bytes.py

├── docker-compose.yml

└── README.md

```



\## Funcionamiento



El servicio permite consultar los datos de un usuario mediante su ID.



La lógica de negocio se encuentra en:



```text

server/service.py

```



La función `obtener\_usuario()` realiza la búsqueda del usuario y es utilizada tanto por el servicio REST como por el servicio gRPC.



\### REST



El servicio REST utiliza Flask y está disponible en el puerto `5000`.



Endpoint:



```text

GET /usuarios/<id>

```



Ejemplo:



```text

GET /usuarios/1

```



Respuesta:



```json

{

&#x20;   "id": 1,

&#x20;   "nombre": "Mauricio",

&#x20;   "edad": 22

}

```



\### gRPC



El servicio gRPC utiliza Protocol Buffers y está disponible en el puerto `50051`.



El contrato del servicio se define en:



```text

service.proto

```



El método utilizado es:



```text

ObtenerUsuario(UsuarioRequest) returns (UsuarioResponse)

```



\## Ejecución con Docker Compose



Para ejecutar el proyecto se requiere tener Docker instalado.



Desde la carpeta `t01`, ejecutar:



```powershell

docker compose up

```



Docker Compose crea los contenedores del servidor y del cliente y los conecta mediante una red común.



El servidor expone:



```text

REST: 5000

gRPC: 50051

```



El cliente utiliza el nombre del servicio `server` para comunicarse con el contenedor del servidor.



Al ejecutarse correctamente, el cliente muestra las respuestas obtenidas mediante ambos protocolos.



\## Construcción de las imágenes



Las imágenes también pueden construirse mediante:



```powershell

docker compose build

```



Para detener y eliminar los contenedores creados por Compose:



```powershell

docker compose down

```



\## Comparación de bytes



Se realizó la misma consulta para el usuario con ID `1` mediante REST y gRPC.



La medición se realizó sobre el contenido de la respuesta. En REST se utilizó `len(respuesta.content)`, mientras que en gRPC se utilizó `SerializeToString()` para obtener el tamaño del mensaje Protocol Buffers serializado.



Los resultados obtenidos fueron:



| Protocolo | Tamaño de la respuesta |

| --------- | ---------------------: |

| REST      |               39 bytes |

| gRPC      |               14 bytes |



En esta prueba, gRPC utilizó 25 bytes menos que REST, equivalente aproximadamente a un 64.1 % menos de datos en el payload de la respuesta.



Esta medición corresponde únicamente al contenido de las respuestas y no representa el tráfico total de red, ya que no incluye las cabeceras ni otros datos asociados a los protocolos de transporte.



\## Manejo de errores



Cuando se solicita un ID que no existe, REST devuelve un código HTTP `404` con un mensaje indicando que no hay datos para el ID solicitado.



gRPC utiliza el estado `NOT\_FOUND` para representar la misma situación.



\## Evidencias



Las evidencias de la implementación, ejecución, pruebas, Docker y comparación de bytes se encuentran documentadas en:



```text

evidencia.md

```



Las capturas utilizadas como evidencia se encuentran en:



```text

evidencias/

```



