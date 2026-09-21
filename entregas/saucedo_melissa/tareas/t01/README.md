# T01 · El mismo servicio, por REST y por gRPC
## Melissa Saucedo

## Diseño

La lógica de negocio vive en un solo lugar: `logica.py`. Ahí está la
"base de datos" en memoria (un diccionario) y la función `get_item(id)`,
que es la única que sabe buscar datos.

Hay dos controladores, y ninguno de los dos repite esa búsqueda:

- `server_rest.py`: expone la lógica por HTTP, usando Flask. La ruta
  `/item/<id>` llama a `get_item(id)` y traduce el resultado a JSON.
- `server_grpc.py`: expone la MISMA lógica por gRPC. El método
  `ObtenerItem` (definido en `servicio.proto`) también llama a
  `get_item(id)`, pero traduce el resultado a un mensaje `ItemResponse`
  de protobuf en vez de JSON.

Lo único que cambia entre los dos controladores es el "empaque":
cómo llega la petición, cómo se llama a la función, y en qué formato
sale la respuesta. La búsqueda de datos en sí es idéntica en ambos,
porque los dos llaman a la misma función de `logica.py`.

`cliente.py` es un solo programa que sabe hablarle a las dos interfaces:
`llamar_rest(id)` usa la librería `requests` para pedir por HTTP,
`llamar_grpc(id)` usa el stub generado a partir del `.proto` para pedir
por gRPC. El host del servidor viene de la variable de entorno
`SERVIDOR_HOST` (por defecto `localhost`, y `server` cuando corre en
Docker Compose, porque así se llama el servicio).

## Cómo se levanta

Con Docker Compose (recomendado):

```
docker compose up --build
```

Esto construye dos imágenes (servidor y cliente), las conecta en la
misma red, y el cliente hace automáticamente una llamada de prueba
(id=1) por las dos interfaces en cuanto arranca.

Sin Docker, en local (necesita dos terminales para los servidores y
una tercera para el cliente):

```
pip install -r requirements.txt
python server_grpc.py      (terminal 1)
python server_rest.py      (terminal 2)
python cliente.py 1        (terminal 3)
```

## Evidencia

Llamando al id=1 (existe):

```
[REST] status=200 body={"carrera":"Ingenieria en Computacion","encontrado":true,"nombre":"Melissa Saucedo"}
[gRPC] encontrado=True nombre=Melissa Saucedo carrera=Ingenieria en Computacion
```

Llamando al id=99 (no existe):

```
[REST] status=404 body={"error":"no encontrado"}
[gRPC] encontrado=False nombre= carrera=
```

Corriendo con docker compose up --build, el cliente hace la misma
llamada automáticamente contra el servidor por su nombre de servicio
(server), confirmando que la comunicación entre contenedores funciona:

```
server-1  | 172.20.0.3 - - [21/Sep/2026 05:47:37] "GET /item/1 HTTP/1.1" 200 -
client-1  | --- Pidiendo el id=1 por las dos interfaces (servidor: server) ---
client-1  | [REST] status=200 body={"carrera":"Ingenieria en Computacion","encontrado":true,"nombre":"Melissa Saucedo"}
client-1  | [gRPC] encontrado=True nombre=Melissa Saucedo carrera=Ingenieria en Computacion
client-1 exited with code 0
```

## Punto extra: bytes por la red

Medí el tamaño de la respuesta REST con curl y wc -c:

```
curl -s http://localhost:5000/item/1 | wc -c
85
```

Para gRPC no hay un comando tan directo porque el mensaje es binario,
así que medí serializando los mismos mensajes que se mandan por la red
con SerializeToString() (script medir_grpc.py):

```
Peticion (ItemRequest) serializada: 3 bytes
Respuesta (ItemResponse) serializada: 46 bytes
Total ida y vuelta: 49 bytes
```

REST: 85 bytes (solo el cuerpo de la respuesta, en JSON)
gRPC: 49 bytes en total (petición + respuesta, en protobuf binario)

gRPC pesa menos porque protobuf es un formato binario compacto: no
repite los nombres de los campos como texto en cada mensaje (como sí
hace JSON con "nombre": y "carrera":), ni usa comillas ni espacios.
JSON es más pesado pero legible por humanos sin herramientas extra;
protobuf es más liviano pero hay que decodificarlo con el .proto para
poder leerlo. Es la misma idea que vimos en clase con el ejemplo de
180 bits contra 60, aunque los números reales salieron distintos
porque el contenido del mensaje también es distinto.
