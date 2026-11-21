# S04 · Docker, día dos

Josué Hernández

## Misión 1. Persistencia

**Sin volumen nombrado.** La fila salió en la primera consulta, al borrar el contenedor y crear otro, la tabla ya no estaba. Postgres crea un volumen anónimo si no le paso uno. Con `--rm` se elimina ese volumen al eliminar el contenedor.

```text
$ docker run --rm -d --name s04-sin-volumen --env-file .env postgres:17-alpine
$ docker exec s04-sin-volumen psql -U postgres -d banco_dev -v ON_ERROR_STOP=1 -c "CREATE TABLE cuentas (id integer PRIMARY KEY, saldo integer);" -c "INSERT INTO cuentas VALUES (1, 100);"
CREATE TABLE
INSERT 0 1
$ docker exec s04-sin-volumen psql -U postgres -d banco_dev -v ON_ERROR_STOP=1 -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)

$ docker rm -f s04-sin-volumen
s04-sin-volumen
$ docker run --rm -d --name s04-sin-volumen --env-file .env postgres:17-alpine
$ docker exec s04-sin-volumen psql -U postgres -d banco_dev -v ON_ERROR_STOP=1 -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
$ docker rm -f s04-sin-volumen
s04-sin-volumen
```

**Con volumen nombrado.** Volví a montar `s04_prueba_datos` al crear el segundo contenedor, la fila con saldo 100 seguía ahí.

```text
$ docker volume create s04_prueba_datos
s04_prueba_datos
$ docker run --rm -d --name s04-con-volumen --env-file .env -v s04_prueba_datos:/var/lib/postgresql/data postgres:17-alpine
$ docker exec s04-con-volumen psql -U postgres -d banco_dev -v ON_ERROR_STOP=1 -c "CREATE TABLE cuentas (id integer PRIMARY KEY, saldo integer);" -c "INSERT INTO cuentas VALUES (1, 100);"
CREATE TABLE
INSERT 0 1
$ docker exec s04-con-volumen psql -U postgres -d banco_dev -v ON_ERROR_STOP=1 -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)

$ docker rm -f s04-con-volumen
s04-con-volumen
$ docker run --rm -d --name s04-con-volumen --env-file .env -v s04_prueba_datos:/var/lib/postgresql/data postgres:17-alpine
$ docker exec s04-con-volumen psql -U postgres -d banco_dev -v ON_ERROR_STOP=1 -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)

$ docker rm -f s04-con-volumen
s04-con-volumen
$ docker volume rm s04_prueba_datos
s04_prueba_datos
```

## Misión 2. Red

Desde `s04_default` pude usar el nombre `db` para llegar a PostgreSQL, luego repetí el comando desde la red por defecto y no hubo respuesta.

```text
$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
```

## Misión 3. Configuración

Usé la misma imagen con dos archivos de variables, cambiaron el nombre de la base y el entorno. La salida de `env` está filtrada para mostrar esas dos variables.

```text
$ docker run --rm -d --name s04-config-dev --env-file .env postgres:17-alpine
$ docker run --rm -d --name s04-config-prod --env-file .env.prod postgres:17-alpine
$ docker exec s04-config-dev env | grep -E '^(POSTGRES_DB|APP_ENV)='
POSTGRES_DB=banco_dev
APP_ENV=desarrollo
$ docker exec s04-config-prod env | grep -E '^(POSTGRES_DB|APP_ENV)='
POSTGRES_DB=banco_prod
APP_ENV=produccion
$ docker rm -f s04-config-dev s04-config-prod
s04-config-dev
s04-config-prod
```

## Misión 4. Compose

Levanté nginx, PostgreSQL, Redis y RabbitMQ con `docker compose up -d --wait`. PostgreSQL quedó en estado `healthy`, nginx devolvió 200 y Redis contestó `PONG`.

```text
$ docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    24 seconds ago   Up 23 seconds             4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 127.0.0.1:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     24 seconds ago   Up 23 seconds             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        24 seconds ago   Up 23 seconds (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       24 seconds ago   Up 16 seconds             127.0.0.1:8080->80/tcp
$ docker compose exec -T cache redis-cli ping
PONG
$ curl -fs -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/
200
```

**¿Qué se pierde con `down` y qué con `down -v`?** El comando "down" destruye los contenedores y la red asociados al proyecto pero deja intacto el volumen. Cuando lo volví a levantar pude ver la fila número cuatro. Si se usa "down -v" entonces también se destruye el volumen y el resultado de la consulta fue "t" (verdadero).

```text
$ docker compose down
$ docker compose up -d --wait
$ docker compose exec -T db psql -U postgres -d banco_dev -Atc "SELECT id FROM prueba_compose;"
4
$ docker compose down -v
$ docker compose up -d --wait
$ docker compose exec -T db psql -U postgres -d banco_dev -Atc "SELECT to_regclass('prueba_compose') IS NULL AS se_borro;"
t
```

**¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?** Los dos están en la red de Compose, docker resuelve el nombre `db` dentro de esa red y el puerto 5432 ya está disponible ahí, publicarlo serviría para conectarse desde el equipo anfitrión.

**¿Qué pasaría si el `.env` estuviera dentro de la imagen?** Los valores podrían quedar guardados en sus capas, quien tenga la imagen podría leer la contraseña y cambiar el archivo incluido exigiría reconstruir la imagen y con `env_file`, puedo cambiar la configuración al crear el contenedor.
