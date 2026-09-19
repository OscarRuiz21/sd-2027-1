# T01 — El mismo servicio por REST y gRPC

**Alumno:** Tristán Qesen Sánchez Mayen

## Diseño

Un único proceso servidor (`server/main.js`) expone el catálogo por REST en el puerto
3000 y por gRPC en 50051. **La lógica compartida está en `server/logic.js`:** recibe
un ID, consulta un diccionario en memoria y devuelve la información del producto
o indica que no hay datos.

`server/rest_api.js` convierte la petición HTTP en una llamada a `consultarPorId` y
traduce el resultado a JSON y códigos HTTP (200 o 404). `server/grpc_server.js` llama a esa misma
función y traduce el resultado a mensajes Protobuf y estados gRPC. Lo único
que cambia entre interfaces es el controlador y el mecanismo de comunicación.

`cliente/cliente.js` realiza peticiones a ambas interfaces para IDs existentes (`101`, `103`) e inexistentes (`999`), mostrando sus respuestas y midiendo los bytes transmitidos.
Cliente y servidor tienen Dockerfiles propios (`Dockerfile.server` y `Dockerfile.client`) y se comunican usando el nombre
`servidor` en la red creada por Compose (`red-sd`). El cliente termina después de consultar;
el servidor permanece activo. El cliente no contiene el catálogo ni su lógica.

## Levantar y ejecutar

Requisito: Docker Desktop iniciado con contenedores Linux y Docker Compose.
Desde la raíz del repositorio o la carpeta de la entrega:

```bash
cd entregas/sanchez_tristan/tareas/t01
docker compose up -d --build
docker compose logs cliente
docker compose ps -a
```

El cliente realiza las peticiones a ambas interfaces y muestra las respuestas.
Es normal que el contenedor `cliente_unificado` aparezca como `Exited (0)` después de mostrar las dos respuestas.
REST queda accesible en http://localhost:3000/api/consulta/101 y gRPC en localhost:50051.

Para ejecutar localmente sin Docker en dos terminales:

```bash
npm install
npm run start:server   # Terminal 1: Servidor unificado
npm run start:client   # Terminal 2: Cliente de pruebas
```

## Contratos

| Caso | REST | gRPC |
|---|---|---|
| Consulta | GET /api/consulta/{id} | servicio.BuscadorService/Consultar |
| ID existente (`101`) | HTTP 200 y producto JSON | OK y ConsultaResponse (`encontrado = true`) |
| ID inexistente (`999`) | HTTP 404 | OK y ConsultaResponse (`encontrado = false`) |

El contrato gRPC está definido en [proto/servicio.proto](proto/servicio.proto).

## Pruebas con curl

```bash
curl -i http://localhost:3000/api/consulta/101
curl -i http://localhost:3000/api/consulta/999
```

Estado HTTP 200 para ID existente:

```json
{"id":"101","informacion":"Laptop Dell XPS 15 - 16GB RAM, 512GB SSD","encontrado":true}
```

Estado HTTP 404 para ID inexistente:

```json
{"id":"999","informacion":"No se encontraron datos para el ID especificado","encontrado":false}
```

## Comparación de bytes y explicación (Punto extra)

Resultados de la medición de carga útil (payload) realizada por `cliente/cliente.js`:

| Consulta ID | REST (JSON) | gRPC (Protobuf) | Diferencia / Ahorro |
|---|---:|---:|---|
| `101` (Existente) | 87 bytes | 49 bytes | 38 bytes menor en gRPC (43.68% de ahorro) |
| `103` (Existente) | 82 bytes | 44 bytes | 38 bytes menor en gRPC (46.34% de ahorro) |
| `999` (Inexistente) | 95 bytes | 56 bytes | 39 bytes menor en gRPC (41.05% de ahorro) |

El mensaje de respuesta en Protobuf es considerablemente más pequeño que en JSON debido a:
- JSON repite en texto plano los nombres de las claves (`"id"`, `"informacion"`, `"encontrado"`), sumando más de 30 bytes solo en llaves de sintaxis.
- Protobuf sustituye los nombres por tags binarios pequeños de 1 byte.
- Los tipos booleanos (`true`/`false`) en JSON ocupan 4-5 bytes ASCII; en Protobuf se codifican en 1 byte.
- En el transporte, REST (HTTP/1.1) envía encabezados en texto plano (~150-200 bytes adicionales por llamada), mientras gRPC (HTTP/2) comprime encabezados con HPACK y solo agrega 5 bytes de prefijo de trama gRPC.

## Para detener

```bash
docker compose down
```
