# Evidencia de la práctica S04

Nombre: Fernando Reyes Vázquez

## Misión 1. Persistencia

### Sin volumen

Se creó el contenedor de PostgreSQL sin utilizar un volumen:

```bash
docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
```

Después se creó la tabla `cuentas`:

```bash
docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
```

La terminal mostró:

```text
CREATE TABLE
```

Posteriormente se agregó una fila con un identificador de 1 y un saldo de 100:

```bash
docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
```

La salida obtenida fue:

```text
INSERT 0 1
```

Para comprobar que la información se había guardado, se consultó la tabla:

```bash
docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
```

Se obtuvo:

```text
 id | saldo
----+-------
  1 |   100
(1 row)
```

Después se eliminó completamente el contenedor:

```bash
docker rm -f db1
```

La terminal confirmó su eliminación:

```text
db1
```

Se creó nuevamente un contenedor con el mismo nombre, la misma imagen y la misma contraseña, pero todavía sin utilizar un volumen:

```bash
docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
```

Después se intentó consultar nuevamente la tabla `cuentas`:

```bash
docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
```

Esta vez se obtuvo el siguiente error:

```text
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

Esto muestra que los datos almacenados directamente dentro del contenedor se perdieron al eliminarlo. Aunque se creó otro contenedor utilizando la misma imagen y el mismo nombre, este comenzó sin la tabla ni la información que se había creado anteriormente.


### Con volumen

Primero se eliminó el contenedor que se estaba utilizando:

```bash
docker rm -f db1
```

La terminal mostró:

```text
db1
```

Después se creó nuevamente el contenedor, pero esta vez utilizando el volumen `datos_banco` conectado al directorio donde PostgreSQL almacena sus datos:

```bash
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
```

Se volvió a crear la tabla `cuentas`:

```bash
docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
```

La salida fue:

```text
CREATE TABLE
```

Después se agregó nuevamente la fila:

```bash
docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
```

Se obtuvo:

```text
INSERT 0 1
```

Posteriormente se eliminó otra vez el contenedor:

```bash
docker rm -f db1
```

La terminal confirmó:

```text
db1
```

Se creó un contenedor nuevo utilizando otra vez el mismo volumen `datos_banco`:

```bash
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
```

Sin volver a crear la tabla ni insertar la fila, se realizó directamente la consulta:

```bash
docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
```

En esta ocasión se obtuvo:

```text
 id | saldo
----+-------
  1 |   100
(1 row)
```

Esto demuestra que el volumen permitió conservar la información aunque el contenedor original fuera eliminado. El nuevo contenedor pudo utilizar los datos que seguían almacenados en `datos_banco`, por lo que la tabla y la fila continuaron disponibles.

## Misión 2. Red

Primero se comprobó que los servicios creados con Docker Compose se encontraban en ejecución y que la base de datos estaba en estado saludable.

```bash
docker compose ps
```

Después se creó un contenedor temporal conectado a la red `s04_default` y se intentó localizar el servicio `db` utilizando únicamente su nombre.

```bash
docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
```

La terminal mostró:

```text
db:5432 - accepting connections
```

Esto demuestra que un contenedor conectado a la misma red de Docker puede encontrar al servicio `db` por su nombre, sin necesidad de conocer directamente su dirección IP.

Después se realizó la misma prueba, pero esta vez sin conectar el contenedor temporal a la red `s04_default`.

```bash
docker run --rm postgres:17-alpine pg_isready -h db
```

La salida obtenida fue:

```text
db:5432 - no response
```

En este caso el contenedor no pudo comunicarse con `db` porque no formaba parte de la misma red. Esto muestra que la resolución de los nombres de los servicios funciona entre los contenedores que comparten la red creada por Docker Compose.


## Misión 3. Configuración

Para comprobar que una misma imagen puede utilizar configuraciones diferentes, se crearon dos archivos de variables de entorno: `.env.dev` y `.env.prod`.

El archivo `.env.dev` se configuró con:

```text
POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
```

Mientras que `.env.prod` se configuró con:

```text
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
```

Primero se eliminaron los contenedores `db_dev` y `db_prod` que se habían utilizado anteriormente:

```bash
docker rm -f db_dev db_prod
```

La terminal confirmó:

```text
db_dev
db_prod
```

Después se creó el contenedor de desarrollo utilizando la imagen `postgres:17-alpine` y el archivo `.env.dev`:

```bash
docker run -d --name db_dev --network redlab --env-file .env.dev postgres:17-alpine
```

También se creó el contenedor de producción utilizando la misma imagen, pero cargando la configuración desde `.env.prod`:

```bash
docker run -d --name db_prod --network redlab --env-file .env.prod postgres:17-alpine
```

Para comprobar las variables recibidas por el contenedor de desarrollo se ejecutó:

```bash
docker exec db_dev env | grep POSTGRES
```

La salida obtenida fue:

```text
POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
```

Después se revisó el contenedor de producción:

```bash
docker exec db_prod env | grep POSTGRES
```

La terminal mostró:

```text
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
```

Los dos contenedores utilizan la misma imagen `postgres:17-alpine`, pero tienen configuraciones diferentes debido a las variables proporcionadas desde archivos externos. Esto permite utilizar una misma imagen en diferentes entornos sin tener que modificarla o reconstruirla.

También se configuró `.gitignore` para evitar subir a GitHub los archivos que contienen configuraciones locales:

```text
.env
.env.dev
.env.prod
```

En cambio, se mantiene `.env.example` como ejemplo de las variables necesarias, utilizando valores que no contienen una contraseña real.


## Misión 4. Docker Compose

Para comprobar el funcionamiento del sistema completo se revisó el estado de los servicios definidos en `compose.yaml`.

```bash
docker compose ps
```

La salida obtenida fue:

```text
NAME           IMAGE                          COMMAND                  SERVICE   CREATED              STATUS                        PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    About an hour ago    Up About an hour              0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     About an hour ago    Up About an hour              6379/tcp
s04-db-1       postgres:17-alpine              "docker-entrypoint.s…"   db        About an hour ago    Up About an hour (healthy)    5432/tcp
s04-web-1      nginx:alpine                    "/docker-entrypoint.…"   web       About an hour ago    Up About an hour              0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

Con esta salida se comprobó que los cuatro servicios definidos en Docker Compose se encontraban en ejecución. También se observó que el servicio `db` se encontraba en estado `healthy`, lo que indica que PostgreSQL estaba listo para aceptar conexiones.

### ¿Qué se pierde con `docker compose down` y qué con `docker compose down -v`?

Durante la práctica se ejecutó:

```bash
docker compose down
```

Este comando eliminó los contenedores y la red creada por Docker Compose. Sin embargo, al revisar los volúmenes con:

```bash
docker volume ls
```

el volumen `s04_datos_banco` todavía existía.

Esto significa que `docker compose down` elimina los contenedores y la red del proyecto, pero conserva los volúmenes y, por lo tanto, los datos almacenados en ellos.

En cambio, al utilizar:

```bash
docker compose down -v
```

también se eliminan los volúmenes asociados al proyecto. En este caso los datos almacenados en esos volúmenes se perderían.

En resumen, `down` elimina los contenedores y la red pero conserva los datos persistentes, mientras que `down -v` también elimina los volúmenes y su información.


### ¿Por qué `web` puede alcanzar a `db` sin publicar el puerto 5432?

Docker Compose crea automáticamente una red para los servicios del proyecto. En esta práctica la red creada fue:

```text
s04_default
```

Los servicios conectados a esta red pueden encontrarse utilizando sus nombres. Por esta razón, el servicio `web` puede comunicarse con `db` utilizando el nombre `db` y su puerto interno `5432`, aunque este puerto no haya sido publicado hacia la computadora.

Publicar un puerto es necesario cuando se quiere acceder a un servicio desde fuera de la red de Docker. Para la comunicación entre servicios que pertenecen a la misma red, no es necesario publicar el puerto.


### ¿Qué pasaría si `.env` estuviera dentro de la imagen?

Si el archivo `.env` estuviera incluido dentro de la imagen, las variables de configuración y posibles contraseñas quedarían almacenadas dentro de ella. Esto podría exponer información sensible a cualquier persona que tuviera acceso a la imagen.

Además, si se quisiera cambiar una variable, sería necesario modificar el archivo y reconstruir la imagen.

Al mantener `.env` fuera de la imagen, se puede utilizar la misma imagen con configuraciones diferentes sin reconstruirla. También se evita incluir directamente las contraseñas dentro de la imagen.
