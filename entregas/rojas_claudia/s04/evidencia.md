# Evidencia - S04

## Misión 1: Persistencia

**Prueba SIN volumen (Los datos mueren)**
Al borrar el contenedor a la fuerza (`docker rm -f`) y volver a levantarlo sin un volumen configurado, la base de datos se reinicia desde cero. Esta es la salida al intentar consultar la tabla de nuevo:

$ docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^

**Prueba CON volumen nombrado (Los datos sobreviven)**
Al levantar el contenedor asignando un volumen (`-v datos_banco:/var/lib/postgresql/data`), borrarlo y recrearlo con el mismo comando, el estado sobrevive. Esta es la salida de la consulta exitosa:

$ docker rm -f db1
db1

$ docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
45eca761f25675e83a44cd05717eb94214083bffb7187b3256a19603231fe664

$ docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)

## Misión 2: Red

**Prueba DENTRO de la red**
Usando un contenedor temporal conectado a `s04_default`, el nombre `db` resuelve correctamente a la base de datos:

$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections


**Prueba FUERA de la red**
Sin especificar la red, el contenedor cae en el bridge por defecto (que no resuelve nombres), por lo que no encuentra a `db`:

$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response


## Misión 3: Configuración

**Configuración en db_dev (desde .env)**
Al revisar las variables de entorno del contenedor inyectado con el archivo `.env`, se observa su configuración específica:

$ docker exec db_dev env | grep POSTGRES
POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev]


**Configuración en db_prod (desde .env.prod)**
La misma imagen levantada con el archivo `.env.prod` adquiere una identidad completamente distinta:

$ docker exec db_prod env | grep POSTGRES
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod

## Misión 4: Compose

**compose.yaml**
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:17-alpine
    env_file: .env
    volumes:
      - datos_banco:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  cache:
    image: redis:8-alpine

  broker:
    image: rabbitmq:4-management-alpine
    ports:
      - "15672:15672"

volumes:
  datos_banco:

**Contenedores activos (docker compose ps)**

$ docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"
NAME           SERVICE   STATUS                   PORTS
s04-broker-1   broker    Up 4 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 4 minutes             6379/tcp
s04-db-1       db        Up 4 minutes (healthy)   5432/tcp
s04-web-1      web       Up 4 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp

**Respuestas a las preguntas**

**1. ¿Qué se pierde con `down` y qué con `down -v`?**
Con `down` se detienen y borran los contenedores y la red, pero se conservan intactos los volúmenes, lo que significa que los datos sobreviven para el próximo arranque. Con `down -v`, además de borrar contenedores y redes, se destruyen todos los volúmenes asociados, lo que resulta en la pérdida de todos los datos y la configuración guardada. 

**2. ¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?**
Porque ambos servicios se conectan internamente a la red que Docker Compose crea para el proyecto. El servidor DNS interno de Docker permite que se encuentren por su nombre de servicio (`web` y `db`), y no necesitan publicar puertos al exterior porque las comunicaciones dentro de la red privada son completamente accesibles entre ellos. Publicar puertos solo es necesario si queremos acceder al servicio desde nuestra máquina host, no desde otro contenedor.

**3. ¿Qué pasaría si el `.env` estuviera dentro de la imagen?**
Estaríamos violando el "Factor III" que indica que la configuración debe vivir en el entorno y no horneada dentro de la imagen. Si las variables estuvieran dentro de la imagen, no podríamos utilizarla en diferentes ambientes sin tener que reconstruirla cada vez, y peor aún, estaríamos exponiendo credenciales sensibles en una imagen que podría ser pública o compartida. La imagen debe ser el "uniforme" inmutable, mientras que el `.env` funciona como el "gafete" que le da su identidad y configuración.
