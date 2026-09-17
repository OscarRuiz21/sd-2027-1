# T01 · El mismo servicio, por REST y por gRPC

## Qué hace el servicio

La "base de datos" es un diccionario con tres productos, cada uno con su clave,
nombre, precio y stock. El servicio recibe la clave de un producto y regresa su información.
Si la clave no existe, responde que no hay datos.

## Dónde está la lógica

Toda la lógica vive en `servidor/logica.py`, el cual contiene, el diccionario `PRODUCTS` y la función `consult(product_id)`, que devuelve el producto o `None` si no existe. Ese archivo no tiene ningún `import` Es una función de Python normal para usar en cualquier script.

Los dos controladores hacen `from logica import consult`, ninguno copia el
diccionario ni la búsqueda. Sí la lógica estuviera repetida en cada controlador, tendría dos programas que mantener sincronizados a mano.

## Qué cambia entre REST y gRPC

|                             | REST (`servidor/rest_controlador.py`)                                  | gRPC (`servidor/grpc_controlador.py`)                                       |
| --------------------------- | ---------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Cómo llega la petición      | FastAPI recibe un `GET /products/{product_id}` y llama a `get_product` | gRPC recibe un mensaje `ProductRequest` y llama a `GetProduct`              |
| Cómo se saca el ID          | Del pedazo de la URL; FastAPI lo convierte a `int`                     | Del campo `request.product_id` del mensaje                                  |
| Llamada a la lógica         | `consult(product_id)`                                                  | `consult(request.product_id)`                                               |
| Cómo se dice "no hay datos" | `raise HTTPException(status_code=404)`                                 | `context.abort(grpc.StatusCode.NOT_FOUND, ...)`                             |
| Cómo sale la respuesta      | `return product`; FastAPI lo convierte a JSON (texto)                  | `return ProductResponse(**product)`; gRPC lo serializa a protobuf (binario) |
| Contrato previo             | Ninguno: la forma del JSON es lo que yo decidí devolver                | Obligatorio: `products.proto`, del que se genera el código                  |

## Contrato

`proto/products.proto` declara el servicio `Products` con un solo método,
`GetProduct(ProductRequest) returns (ProductResponse)`, y la forma de los dos mensajes:
`ProductRequest` lleva un `int32 product_id`, y `ProductResponse` lleva `string name`,
`int32 value` e `int32 stock`.

Con `grpc_tools.protoc` se generan `products_pb2.py` (las clases de los mensajes) y
`products_pb2_grpc.py` (el molde `ProductsServicer` para el servidor y el `ProductsStub` para
el cliente). Los dos lados necesitan esos archivos porque el contrato tiene dos lados: el
servidor lo implementa y el cliente lo usa para llamar. Por eso cada Dockerfile los genera
a partir del mismo `.proto`.

## Estructura

```
t01/
├── proto/products.proto        contrato gRPC
├── servidor/
│   ├── logica.py               lógica de negocio (única)
│   ├── rest_controlador.py     traductor HTTP/JSON → lógica
│   ├── grpc_controlador.py     traductor gRPC/protobuf → lógica
│   ├── main.py                 un proceso: REST en :8000 y gRPC en :50051
│   └── Dockerfile
├── cliente/
│   ├── cliente.py              llama a las dos interfaces y compara
│   ├── medir_bytes.py          punto extra
│   └── Dockerfile
├── compose.yaml                red + orden de arranque
└── evidencia/
```

Los `products_pb2*.py` son generados a partir del `.proto`

`main.py` arranca los dos servidores en un solo proceso: gRPC en hilos de fondo y uvicorn al frente.

## Cómo se levanta

Con Docker:

```bash
docker compose up --build
```

Construye las dos imágenes, crea la red `t01_default`, arranca el servidor, espera a que su
healthcheck responda, y entonces corre el cliente, que imprime las 4 consultas y termina.
El servidor se queda arriba en `localhost:8000` (REST) y `localhost:50051` (gRPC).

Cada imagen se describe en su Dockerfile: una para el servidor y otra para el cliente.
Compose es la orquesta: las construye, las arranca en orden y las pone en una red privada donde cada contenedor se ve por su nombre de servicio.

## Evidencia

| Archivo                             | Qué muestra                                                         |
| ----------------------------------- | ------------------------------------------------------------------- |
| `evidencia/rest_curl.txt`           | `curl` a REST: 200 con datos y 404 sin datos                        |
| `evidencia/rest_servidor_log.txt`   | log de uvicorn con esas peticiones                                  |
| `evidencia/servidor_local.txt`      | REST y gRPC contra `main.py` local, incluido el `NOT_FOUND`         |
| `evidencia/cliente_local.txt`       | el cliente comparando las dos vías, local                           |
| `evidencia/docker_compose_logs.txt` | lo mismo, dentro de Docker: healthcheck, cliente desde `172.20.0.3` |
| `evidencia/docker_compose_ps.txt`   | estado de los contenedores                                          |
| `evidencia/docker_network.txt`      | la red `t01_default` con los dos contenedores                       |
| `evidencia/docker_curl_externo.txt` | `curl` desde la Mac al contenedor por el puerto mapeado             |
| `evidencia/medicion_bytes.txt`      | punto extra                                                         |

## Punto extra

Medido con `cliente/medir_bytes.py` para `GET producto 2`, en la **capa de aplicación**:

|                          | Petición | Respuesta | Total     | Solo datos       |
| ------------------------ | -------- | --------- | --------- | ---------------- |
| REST (HTTP/1.1 + JSON)   | 133 B    | 164 B     | **297 B** | 39 B de JSON     |
| gRPC (HTTP/2 + protobuf) | 7 B      | 17 B      | **24 B**  | 12 B de protobuf |

Cómo se midió:

- REST: `requests` conserva la petición enviada y la respuesta, se reconstruye el texto
  HTTP/1.1 y se cuentan sus bytes.
- gRPC: `ByteSize()` del mensaje protobuf más el marco de 5 bytes que gRPC antepone. **No** incluye las cabeceras HTTP/2, que van comprimidas con HPACK y dependen del estado de la conexión.
