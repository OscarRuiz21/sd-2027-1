# T01 · Venta de boings: el mismo servicio por REST y por gRPC

**Autor:** Javier Velasco

## 1. Qué hice

El sistema que yo realice fue un servicio para la venta de Boings, debido a que me gusta el producto . La forma en la que funciona a es se le asigna un ID a un boing , y con este su sabor, precio y stock. Los boings están guardados en memoria, solo estan habilitados los IDs 1, 2, 3 y 4.

La tarea se realizo en JavaScript con Node
## 2. Cómo está organizado

El proyecto se encuentra organizado en diferentes carpetas, de acuerdo con la función que cumple cada uno de sus componentes.

En la carpeta server/ está todo lo del servidor: core.js tiene la  idea de buscar un boing por su ID en un diccionario, y sobre ella, restController.js para REST y grpcController.js para gRPC, que solo traducen entre su protocolo. 
 
El archivo main.js se encarga de iniciar ambos controladores. De esta manera, el servidor puede ofrecera el servicio mediante REST y gRPC utilizando los puertos 8080 y 50051 respectivamente.

 En la carpeta client/ está client.js, que sabe llamar al servidor por REST, por gRPC o por los dos, y que también mide tiempos. En la carpeta proto/ está boings.proto, el contrato que define la llamada de gRPC y la forma de sus mensajes, y que usan tanto el servidor como el cliente. 
 
 Cada uno, server/ y client/, tiene su propio Dockerfile para construir su contenedor, y el docker-compose.yml para levantarla juntos en la red red-boings.

## 3.  REST Y gRPC

Podria decir que cada controlador es solo un traductor. En donde se recibe algo en su contexto, y en este caso se llama a la función de core.js, y devuelve la respuesta en su contexto. La logica es la siguiente:

```
[REST] GET /boings/1
[gRPC] ObtenerBoing(id=1)
```

### Configuracion y metodos

| | REST | gRPC |
|---|---|---|
| Archivo | restController.js | grpcController.js |
| Puerto | 8080 | 50051 |
| Protocolo | HTTP/1.1 | HTTP/2 |
| Formato de los datos | JSON | Protobuf |
| Contrato | Convención de rutas y campos | El archivo boings.proto |
| Cómo pido el boing 1 | GET /boings/1 |ObtenerBoing({ id: 1 }) |
| Si el ID no existe |  {"encontrado": false, "mensaje": "no hay datos"} | encontrado = false |

 REST es sencillo: el cliente manda una petición escrita en texto y el servidor responde. En cambio, gRPC usa HTTP/2, y cuando arranca, el cliente y el servidor primero se mandan un saludo y sus configuraciones, y en la primera llamada se envían completas.

## 4. Cómo levantar

Estos comandos me ayudo a leventar y hacer pruebas:

```bash
# Levantar el servidor
docker compose up -d --build servidor
# Llamar por REST y por gRPC
docker compose --profile cliente run --rm cliente --id 1
# Un ID que no existe
docker compose --profile cliente run --rm cliente --id 99
# Llamar por protocolo grpc
docker compose --profile cliente run --rm cliente --id 2 --protocolo grpc
# Bajar
docker compose down
```

## 5. ¿Cuántos bytes viajan?

Medí los bytes con tcpdump, que registra lo que viaja por la red. Lo puse en un contenedor extrajunto al servidor, hice una llamada por cada protocolo y sumé los bytes de ida y de vuelta. El script medir_bytes.sh lo hace automáticamente.

### Medición 1: 1 llamada
**Resultado (id = 1):**

| Protocolo | Bytes |
|---|---|
| REST | 427 |
| gRPC | 464 |

El mensaje de gRPC por sí solo es más chico que el JSON. Pero como solo hice una llamada, ese ahorro se pierde entre los bytes del saludo inicial de HTTP/2.

Entonces, para una sola llamada, gRPC no ganó en bytes. La ventaja de gRPC se vería con muchas llamadas seguidas por la misma conexión, porque ese saludo inicial se escribe.

### Medición 2: 100 llamadas 

Para comprobar mi idea de que la ventaja de gRPC aparece con varias llamadas:

| Protocolo | Bytes totales (100 llamadas) | Bytes por llamada |
|---|---|---|
| REST | 42,700 | 427 |
| gRPC | 13,235 | 132 |

Con REST, cada llamada sigue pesando lo mismo que cuando hice una sola (427 bytes), porque cada petición manda sus cabeceras completas otra vez. Con gRPC, el costo por llamada bajó mucho, de 464 a unos 132 bytes, porque el saludo inicial de HTTP/2 se paga una sola vez.


## 6. Medición de tiempo

Para medir el tiempo de respuesta, el cliente cuenta con el modo --tiempos, que utiliza performance.now() para registrar cuánto tarda cada llamada. Primero se realiza una llamada inicial, que incluye el establecimiento de la conexión, y después se realizan 200 llamadas adicionales con la conexión ya abierta.

```bash
docker compose --profile cliente run --rm cliente --id 1 --tiempos --veces 200
```

**Resultado:**

| Protocolo | 1ª llamada (ms) | Promedio (ms) | Mediana (ms) | promedio 95% (ms) | Mínimo (ms) |
|---|---|---|---|---|---|
| REST | 28.28 | 2.20 | 2.03 | 3.43 | 0.59 |
| gRPC | 19.39 | 1.46 | 1.31 | 2.73 | 0.66 |

Con la conexión ya establecida, gRPC obtuvo menores tiempos que REST. La mediana fue de 1.31 ms para gRPC frente a 2.03 ms para REST. Sin embargo, el tiempo mínimo fue prácticamente igual en ambos casos. Una posible explicación es que gRPC utiliza una conexión HTTP/2 y mensajes binarios, mientras que REST utiliza solicitudes HTTP

## 7. Qué aprendí

Pude aprender que REST y gRPC pueden dar exactamente la misma respuesta, aunque por dentro funcionan de manera diferente, principalmente por el formato de los datos, el contrato y la forma de manejar los errores. Aprendi que un solo dato no siempre sirve para comparar dos tecnologias, ya que gRPC uso mas bytes en una llamada, pero tuvo mejores tiempos cuando hice muchas llamadas con la conexion ya abierta.
