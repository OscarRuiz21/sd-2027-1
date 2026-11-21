# T01 El mismo servicio, por REST y por gRPC

Escribí un servicio de catálogo que recibe un ID y devuelve la información del
artículo, y lo expuse dos veces, una por REST con HTTP/1.1 y JSON, y otra por gRPC
con HTTP/2 y Protocol Buffers. Usé Python 3.12, FastAPI, `grpcio` y Docker.

Como lo importante era no terminar con dos programas, lo armé alrededor de un solo
archivo con la lógica y dos adaptadores que lo llaman. Los archivos de código van
sin comentarios, toda la explicación está aquí.

## Dónde quedó la lógica

Toda está en `nucleo.py` y en ningún otro lado. Ese archivo no importa `fastapi` ni
`grpc`, no sabe qué es un puerto ni un código de estado. Expone la función
`consultar_articulo(id)`, la excepción `ArticuloNoEncontrado` para cuando el ID no
existe, y `listar_ids()` para la demo. La base de datos es un diccionario que vive
ahí mismo.

Para que se pudiera comprobar desde afuera y no solo creerme la palabra, metí una
regla chiquita pero observable. `normalizar_id()` le hace `strip()` y `upper()` al
ID antes de buscarlo, así que si pido `"a-100 "` en minúsculas y con espacio, las
dos interfaces me tienen que devolver el mismo artículo. Ninguno de los dos
controladores implementa esa normalización, la heredan del núcleo. Si la lógica
estuviera duplicada yo habría tenido que escribir ese `strip().upper()` dos veces,
y con que se me olvidara en una empezarían a comportarse distinto. Por eso ese caso
está en la lista de IDs que prueba el cliente.

Además, el servidor levanta las dos interfaces en un solo proceso. `main.py`
arranca el servidor de gRPC, que corre en su propio pool de hilos y no bloquea, y
deja a uvicorn en el hilo principal. O sea que no es que cada interfaz tenga su
copia del catálogo, las dos leen el mismo diccionario en memoria.

## Qué cambia entre un controlador y el otro

Los dos hacen las mismas tres cosas. Reciben la petición, llaman al núcleo y
devuelven la respuesta. Si les quito la traducción de entrada y de salida, a los
dos les queda la misma línea en medio, `nucleo.consultar_articulo(id)`.

Cambia cómo llega el ID, porque en REST viaja en la URL y FastAPI me lo entrega
desde el path, mientras que en gRPC llega como campo de un mensaje ya
deserializado. Cambia cómo se arma la respuesta, porque el núcleo devuelve un
diccionario que en REST se vuelve JSON solo, y en gRPC tengo que meterlo en el
mensaje del contrato con `catalogo_pb2.Articulo(**articulo)`. Y cambia el
transporte, texto plano que se puede leer con `curl` contra binario sobre HTTP/2,
que ni siquiera se puede probar con `curl` y por eso escribí un `prueba_grpc.py`
aparte.

La diferencia que más me gustó es la del error. `ArticuloNoEncontrado` no es un 404
ni es un `NOT_FOUND`, es una excepción de Python que no sabe nada de protocolos, y
cada controlador la traduce al código de error de su mundo. El de REST la vuelve
`HTTPException(404)` y el de gRPC la vuelve `contexto.abort(StatusCode.NOT_FOUND)`.
El mensaje de texto es idéntico en los dos porque sale del núcleo, lo único que
cambia es el envoltorio. Si agregara una tercera interfaz, la traducción nueva
iría en el adaptador nuevo y el núcleo no se tocaría.

Ninguna de esas diferencias es de negocio. Cambiar de REST a gRPC no cambió ni una
regla del servicio, solo cómo se empaqueta la pregunta y la respuesta.

## El contrato

```protobuf
service Catalogo {
  rpc Consultar (PeticionArticulo) returns (Articulo);
  rpc Listar    (PeticionVacia)    returns (ListaIds);
}

message PeticionArticulo { string id = 1; }

message Articulo {
  string id          = 1;
  string nombre      = 2;
  string categoria   = 3;
  double precio      = 4;
  int32  existencias = 5;
}
```

Los números de campo son lo que viaja en el binario en lugar del nombre, y de ahí
sale casi toda la diferencia de tamaño que reporto abajo. No entregué los stubs
`catalogo_pb2.py` porque se generan con `grpc_tools.protoc` durante el
`docker build`, y así el `.proto` no puede quedar desfasado del código.

## Cómo se levanta

Lo corrí en PowerShell sobre Windows.

```powershell
docker compose up --build
```

Eso construye las dos imágenes, levanta el servidor en la red bridge `red-t01`,
espera su healthcheck y luego corre el cliente, que hace la demo de las dos
interfaces y después la medición de bytes. El cliente encuentra al servidor por el
DNS de Docker, no por localhost.

Dos cosas de PowerShell que me costaron rato. `curl` ahí es un alias de
`Invoke-WebRequest` y no entiende `-s -i`, hay que llamar a `curl.exe`. Y `$?` es
un booleano, el código de salida está en `$LASTEXITCODE`.

## La evidencia

Las evidencias las tomé como capturas de pantalla y van en
`Capturas_de_pantalla.pdf`. Son siete, en este orden, y estos son los comandos que
corrí para cada una.

1. El build con las dos imágenes listadas, con `docker compose build` y
   `docker images | Select-String t01`.
2. El servidor levantado, con `docker compose up -d servidor`, `docker compose ps`
   y `docker compose logs servidor`. Ahí se ve que un solo proceso levantó los dos
   puertos y que el healthcheck lo marca como `healthy`.
3. La interfaz REST probada con `curl.exe -s -i`, primero con `A-100`, luego con
   `a-100%20` para que se vea la normalización del núcleo, y al final con `z-999`
   para el 404.
4. La interfaz gRPC probada con
   `docker compose run --rm cliente python -u prueba_grpc.py`, que devuelve el
   mensaje `Articulo` y, para el ID inexistente, `StatusCode.NOT_FOUND` con el
   mismo texto que dio el 404.
5. El cliente completo con `docker compose run --rm cliente python -u cliente.py`,
   que cierra con las cuatro consultas coincidiendo entre las dos interfaces.
6. La medición de bytes con
   `docker compose run --rm cliente python -u medicion.py`.
7. La red con `docker network inspect red-t01`, donde se ven los contenedores
   colgados de la misma red bridge con sus IPs internas, que es lo que muestra que
   se hablan por DNS de Docker y no por localhost.

Algo que vale la pena notar en la captura 5. El cliente no compara el código del
protocolo sino el resultado de negocio, porque un 404 y un `NOT_FOUND` son la misma
decisión en dos idiomas. Si alguna pareja no coincidiera saldría con código
distinto de cero, así que la ejecución misma funciona como prueba.

## Punto extra, cuántos bytes viajan

No usé `tcpdump`. Escribí un proxy TCP contador dentro del cliente, en
`medicion.py`, que se pone en medio, reenvía todo byte por byte y suma el `len()`
de cada bloque. Cuento payload TCP, o sea cabeceras HTTP, cuerpo JSON, frames de
HTTP/2 y mensajes protobuf, pero no las cabeceras de TCP e IP, que son iguales para
los dos y solo diluirían la comparación. Lo hice así porque no necesita root y
corre igual dentro del contenedor.

```
  REST  HTTP/1.1 + JSON, conexion nueva              161       237      398
  REST  HTTP/1.1 + JSON, conexion reusada            161       237      398
  REST  HTTP/1.1 minimo escrito a mano                50       237      287
  gRPC  HTTP/2 + protobuf, canal nuevo               415       301      716
  gRPC  HTTP/2 + protobuf, canal reusado             118       157      275

    JSON de respuesta            :   111 B
    protobuf Articulo serializado:    59 B
```

En caliente gRPC gastó 275 bytes contra 398 de REST, 1.45 veces menos. En frío
gastó 716 y perdió por casi el doble. En REST la fila en frío y la de en caliente
dan igual y no es error, HTTP/1.1 no negocia nada a nivel de aplicación, así que la
primera petición pesa lo mismo que la número cien.

El ejemplo de clase da 3 a 1 pero mide otra cosa, compara cómo se codifica un campo
y no cuánto pesa una llamada. La carga útil sí se parece, 111 bytes de JSON contra
59 de protobuf, porque JSON escribe el nombre de cada campo como texto y protobuf
lo cambia por una etiqueta de un byte. Pero de los 398 bytes de REST solo 111 son
el JSON, el resto son cabeceras en texto plano que HTTP/1.1 retransmite completas
en cada petición. Por eso agregué la fila del REST mínimo escrito a mano, donde con
puro `Host` la petición baja de 161 a 50 bytes, o sea que buena parte de lo que uno
mide como REST es ceremonia de la librería.

Del otro lado, gRPC cobra por adelantado. En el canal nuevo van el preface de
HTTP/2, los `SETTINGS` y el primer `HEADERS` con la tabla HPACK vacía, y por eso
gasta 716. En la segunda llamada esas cabeceras ya están indexadas y cuestan uno o
dos bytes, así que la subida cae de 415 a 118. gRPC amortiza y REST no. Con una
llamada suelta gana REST, y la ventaja de gRPC crecería con respuestas más grandes,
porque su costo fijo es constante mientras que JSON repite los nombres de campo en
cada elemento de una lista.
