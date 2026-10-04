# S04 · Docker día dos

Alumno: Derek Santana Cardoso · GitHub: DerekMstn114  
Rama de entrega: `entregas_santana_derek`. Pruebas reales: 4 de octubre de 2026 (hora de Ciudad de México).

## Guía y alcance

Seguí el [Lab S04 del profesor](https://github.com/OscarRuiz21/sd-2027-1/blob/bb70d47b7ed4561aa589f9a5a2fe48118d615aef/labs/s04-docker-dia-2/Lab-S04-Docker-dia-2.html), comprobado contra `main` en el commit `bb70d47b7ed4561aa589f9a5a2fe48118d615aef`, y [labs/README.md](https://github.com/OscarRuiz21/sd-2027-1/blob/bb70d47b7ed4561aa589f9a5a2fe48118d615aef/labs/README.md). T01 (REST y gRPC) es una tarea separada; aquí se entrega el reto de cuatro misiones de Docker. La [política de entregas](https://github.com/OscarRuiz21/sd-2027-1/blob/bb70d47b7ed4561aa589f9a5a2fe48118d615aef/ENTREGAS.md) pide push a la rama personal y un único PR al final del curso.

La fecha original de S04 fue el 13 de septiembre a las 23:59; estas pruebas posteriores no prueban una entrega puntual. La entrega efectiva requiere el push que hará el alumno después de revisar.

Se conservan las imágenes y el ejemplo del profesor: PostgreSQL 17 Alpine, nginx, Redis 8 Alpine y RabbitMQ 4 con consola. Aislé el proyecto con `name: s04_santana_derek`, la red `s04_santana_derek_default` y el volumen `s04_santana_derek_datos_banco`. Uso localhost:8084 para nginx y localhost:15674 para RabbitMQ. S02 usa 9090/9091 y S03 usa 8083; no los detuve ni reutilicé sus recursos.

## Cómo reproducir el sistema

Desde esta carpeta, copia `.env.example` a `.env`, cambia `POSTGRES_PASSWORD` por una contraseña local y ejecuta:

```bash
cp .env.example .env
```

Después de editar `.env`:

```bash
docker compose up -d --wait
docker compose ps
```

La base se llama `banco`. `web` espera a que `db` pase el healthcheck. PostgreSQL, Redis y el puerto AMQP de RabbitMQ no se publican al host. Puedes abrir http://localhost:8084 y http://localhost:15674 (usuario y contraseña de práctica: `guest`). La consola Overview se abrió y se comprobó con ese usuario el 4 de octubre.

`.env` y `.env.prod` son locales, están ignorados y no se entregan. Para repetir la misión 3 crea `.env.prod` con los mismos nombres de variables, otra contraseña local y `POSTGRES_DB=banco_prod`. `.env.example` solo contiene valores de ejemplo. Las contraseñas se sustituyeron por `[OCULTA]` en las salidas; no se cambió ningún resultado. El filtro POSTGRES muestra solo esas variables de la salida real de `docker exec … env`.

## Qué demuestran las misiones

1. **Persistencia:** creé `cuentas(id, saldo)` e inserté `(1,100)`. Sin un volumen nombrado reutilizado, tras `docker rm -f` y la recreación aparece el error de tabla inexistente. Con `s04_santana_derek_m1` montado en `/var/lib/postgresql/data`, la misma fila sobrevivió. La imagen de PostgreSQL declara un volumen anónimo aun cuando no pongo `-v`: ese volumen no se reutiliza automáticamente con el nuevo contenedor. Por eso el error no significa que se haya borrado físicamente cada archivo del volumen anterior.
2. **Red:** un cliente PostgreSQL temporal resolvió `db` dentro de la red del proyecto. El mismo comando fuera de esa red respondió `no response`, con código 2. No usé IPs fijas ni publiqué 5432.
3. **Configuración:** dos contenedores de la misma imagen y del mismo ID de imagen recibieron `.env` y `.env.prod` mediante `--env-file`. Sus bases fueron `banco` y `banco_prod`; no reconstruí ninguna imagen.
4. **Compose:** el archivo declara los cuatro servicios, `env_file`, el volumen nombrado y el healthcheck. `ps` mostró cuatro servicios arriba y `db` healthy; Redis respondió PONG, nginx y la consola devolvieron HTTP 200, RabbitMQ respondió a su diagnóstico.

## Respuestas de la misión 4

**¿Qué se pierde con `down` y qué con `down -v`?** `down` borra los contenedores y la red de este proyecto. Sus capas de escritura se pierden, pero el volumen nombrado se conserva: la fila siguió después de `up`. `down -v` también elimina el volumen nombrado del proyecto y los anónimos adjuntos a sus contenedores: al volver a levantarlo ya no existía `cuentas`. La prueba destructiva se hizo únicamente con datos de práctica de S04. `stop` pausa los contenedores y `start` los reanuda; también comprobé que conservaban la fila. Redis y RabbitMQ aquí no tienen volúmenes nombrados para conservar su estado entre recreaciones.

**¿Por qué `web` alcanza a `db` sin publicar 5432?** Comparten la red definida por Compose. El DNS de Docker registra el servicio `db`; la comunicación interna va a `db:5432`. Publicar un puerto se necesita para acceder desde fuera de esa red. nginx en este ejemplo sirve su página y no consulta SQL por sí solo; la prueba de conexión la hizo otro contenedor en la misma red.

**¿Qué pasaría si `.env` estuviera dentro de la imagen?** La contraseña quedaría distribuida con la imagen y la configuración quedaría ligada a ella. Cambiarla requeriría sustituirla o reconstruirla, y sería fácil exponer el secreto. Con `env_file` la misma imagen recibe configuraciones distintas al crear cada contenedor. En PostgreSQL, cambiar la variable después de inicializar el volumen no cambia por sí solo la contraseña de la base existente.

## Salidas reales

Se omitió `-it` para capturar texto automáticamente. Los códigos distintos de cero que aparecen en las contrapruebas son intencionales y se verificaron. Para descargar las imágenes se usó una configuración Docker anónima temporal porque el llavero devolvía `Keychain Error (-67674)`; no se alteró la configuración del usuario.

## Entorno y fecha real

2026-10-04T07:13:11.832732-06:00

```text
$ docker version --format 'Cliente {{.Client.Version}}; servidor {{.Server.Version}}; arquitectura {{.Server.Arch}}'
Cliente 29.4.1; servidor 29.4.1; arquitectura amd64
[código de salida: 0]
```

```text
$ docker compose version
Docker Compose version v5.1.3
[código de salida: 0]
```


## Misión 1: sin volumen nombrado

```text
$ docker run -d --name s04-derek-sin-volumen --label practica=s04_santana_derek --env-file .env postgres:17-alpine
80ff9d9f7f969fcb70b6b8cafb12bd58efa3754f2723c102d1c5f6e3251fa0d4
[código de salida: 0]
```

```text
$ docker exec s04-derek-sin-volumen psql -U postgres -d banco -c 'CREATE TABLE cuentas(id int primary key, saldo int);'
CREATE TABLE
[código de salida: 0]
```

```text
$ docker exec s04-derek-sin-volumen psql -U postgres -d banco -c 'INSERT INTO cuentas VALUES (1, 100);'
INSERT 0 1
[código de salida: 0]
```

```text
$ docker exec s04-derek-sin-volumen psql -U postgres -d banco -c 'SELECT * FROM cuentas;'
 id | saldo 
----+-------
  1 |   100
(1 row)

[código de salida: 0]
```

```text
$ docker rm -f s04-derek-sin-volumen
s04-derek-sin-volumen
[código de salida: 0]
```

```text
$ docker run -d --name s04-derek-sin-volumen --label practica=s04_santana_derek --env-file .env postgres:17-alpine
12046b5cd8c534ef256f2f0f8a6e1a2427e8f1d2bc60cdaca002facdabd334b4
[código de salida: 0]
```

```text
$ docker exec s04-derek-sin-volumen psql -U postgres -d banco -c 'SELECT * FROM cuentas;'
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
[código de salida: 1]
```

```text
$ docker rm -fv s04-derek-sin-volumen
s04-derek-sin-volumen
[código de salida: 0]
```


## Misión 1: con volumen nombrado

```text
$ docker run -d --name s04-derek-con-volumen --label practica=s04_santana_derek --env-file .env -v s04_santana_derek_m1:/var/lib/postgresql/data postgres:17-alpine
57fdbc11c231a56346994b35097497a3acda0036e3844176cfb4c85622bd0e29
[código de salida: 0]
```

```text
$ docker exec s04-derek-con-volumen psql -U postgres -d banco -c 'CREATE TABLE cuentas(id int primary key, saldo int);'
CREATE TABLE
[código de salida: 0]
```

```text
$ docker exec s04-derek-con-volumen psql -U postgres -d banco -c 'INSERT INTO cuentas VALUES (1, 100);'
INSERT 0 1
[código de salida: 0]
```

```text
$ docker exec s04-derek-con-volumen psql -U postgres -d banco -c 'SELECT * FROM cuentas;'
 id | saldo 
----+-------
  1 |   100
(1 row)

[código de salida: 0]
```

```text
$ docker rm -f s04-derek-con-volumen
s04-derek-con-volumen
[código de salida: 0]
```

```text
$ docker run -d --name s04-derek-con-volumen --label practica=s04_santana_derek --env-file .env -v s04_santana_derek_m1:/var/lib/postgresql/data postgres:17-alpine
226a4475aa200841e96f3ff6de4ccfab91d55ef431ff8a21c58546323d8e4bdc
[código de salida: 0]
```

```text
$ docker exec s04-derek-con-volumen psql -U postgres -d banco -c 'SELECT * FROM cuentas;'
 id | saldo 
----+-------
  1 |   100
(1 row)

[código de salida: 0]
```

```text
$ docker volume inspect s04_santana_derek_m1 --format '{{.Name}}: {{.Driver}}; {{.Mountpoint}}'
s04_santana_derek_m1: local; /var/lib/docker/volumes/s04_santana_derek_m1/_data
[código de salida: 0]
```

```text
$ docker rm -f s04-derek-con-volumen
s04-derek-con-volumen
[código de salida: 0]
```


## Misión 3: misma imagen, dos archivos de configuración

```text
$ docker run -d --name s04-derek-dev --env-file .env postgres:17-alpine
8bbf4f2eac6a8f9d2bc84d40d4bbee43a102b577fa04552d80afe053bac0fb53
[código de salida: 0]
```

```text
$ docker exec s04-derek-dev env | filtrar POSTGRES
POSTGRES_PASSWORD=[OCULTA]
POSTGRES_DB=banco
```

```text
$ docker inspect s04-derek-dev --format 'Imagen {{.Config.Image}}; ID {{.Image}}'
Imagen postgres:17-alpine; ID sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24
[código de salida: 0]
```

```text
$ docker exec s04-derek-dev psql -U postgres -d banco -c 'SELECT current_database();'
 current_database 
------------------
 banco
(1 row)

[código de salida: 0]
```

```text
$ docker rm -fv s04-derek-dev
s04-derek-dev
[código de salida: 0]
```

```text
$ docker run -d --name s04-derek-prod --env-file .env.prod postgres:17-alpine
bb5616c393c36d7813f5fce36b183071826f22aea68977650288096e4f4e265c
[código de salida: 0]
```

```text
$ docker exec s04-derek-prod env | filtrar POSTGRES
POSTGRES_PASSWORD=[OCULTA]
POSTGRES_DB=banco_prod
```

```text
$ docker inspect s04-derek-prod --format 'Imagen {{.Config.Image}}; ID {{.Image}}'
Imagen postgres:17-alpine; ID sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24
[código de salida: 0]
```

```text
$ docker exec s04-derek-prod psql -U postgres -d banco_prod -c 'SELECT current_database();'
 current_database 
------------------
 banco_prod
(1 row)

[código de salida: 0]
```

```text
$ docker rm -fv s04-derek-prod
s04-derek-prod
[código de salida: 0]
```


## Misión 4: sistema Compose

```text
$ docker compose config --quiet
[código de salida: 0]
```

```text
$ docker compose up -d --wait --wait-timeout 120
 Network s04_santana_derek_default Creating 
 Network s04_santana_derek_default Created 
 Volume s04_santana_derek_datos_banco Creating 
 Volume s04_santana_derek_datos_banco Created 
 Container s04_santana_derek-cache-1 Creating 
 Container s04_santana_derek-broker-1 Creating 
 Container s04_santana_derek-db-1 Creating 
 Container s04_santana_derek-cache-1 Created 
 Container s04_santana_derek-db-1 Created 
 Container s04_santana_derek-web-1 Creating 
 Container s04_santana_derek-broker-1 Created 
 Container s04_santana_derek-web-1 Created 
 Container s04_santana_derek-broker-1 Starting 
 Container s04_santana_derek-cache-1 Starting 
 Container s04_santana_derek-db-1 Starting 
 Container s04_santana_derek-broker-1 Started 
 Container s04_santana_derek-db-1 Started 
 Container s04_santana_derek-db-1 Waiting 
 Container s04_santana_derek-cache-1 Started 
 Container s04_santana_derek-db-1 Healthy 
 Container s04_santana_derek-web-1 Starting 
 Container s04_santana_derek-web-1 Started 
 Container s04_santana_derek-db-1 Waiting 
 Container s04_santana_derek-cache-1 Waiting 
 Container s04_santana_derek-broker-1 Waiting 
 Container s04_santana_derek-web-1 Waiting 
 Container s04_santana_derek-web-1 Healthy 
 Container s04_santana_derek-cache-1 Healthy 
 Container s04_santana_derek-db-1 Healthy 
 Container s04_santana_derek-broker-1 Healthy 
[código de salida: 0]
```

```text
$ docker compose ps
NAME                         IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04_santana_derek-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    14 seconds ago   Up 13 seconds             127.0.0.1:15674->15672/tcp
s04_santana_derek-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     14 seconds ago   Up 13 seconds             6379/tcp
s04_santana_derek-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        14 seconds ago   Up 13 seconds (healthy)   5432/tcp
s04_santana_derek-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       14 seconds ago   Up 1 second               127.0.0.1:8084->80/tcp
[código de salida: 0]
```

```text
$ docker compose exec -T cache redis-cli ping
PONG
[código de salida: 0]
```

```text
$ docker compose exec -T db psql -U postgres -d banco -c 'SELECT current_database();'
 current_database 
------------------
 banco
(1 row)

[código de salida: 0]
```

```text
$ docker network inspect s04_santana_derek_default --format '{{range .Containers}}{{.Name}} {{.IPv4Address}}{{println}}{{end}}'
s04_santana_derek-cache-1 172.19.0.4/16
s04_santana_derek-db-1 172.19.0.3/16
s04_santana_derek-broker-1 172.19.0.2/16
s04_santana_derek-web-1 172.19.0.5/16

[código de salida: 0]
```


## Misión 2: cliente dentro y fuera de la red

```text
$ docker run --rm --network s04_santana_derek_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
[código de salida: 0]
```

```text
$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
[código de salida: 2]
```


## Ciclo de vida: stop, start, down y down -v

```text
$ docker compose exec -T db psql -U postgres -d banco -c 'CREATE TABLE cuentas(id int primary key, saldo int); INSERT INTO cuentas VALUES (1,100);'
CREATE TABLE
INSERT 0 1
[código de salida: 0]
```

```text
$ docker compose stop
 Container s04_santana_derek-broker-1 Stopping 
 Container s04_santana_derek-web-1 Stopping 
 Container s04_santana_derek-cache-1 Stopping 
 Container s04_santana_derek-web-1 Stopped 
 Container s04_santana_derek-db-1 Stopping 
 Container s04_santana_derek-cache-1 Stopped 
 Container s04_santana_derek-db-1 Stopped 
 Container s04_santana_derek-broker-1 Stopped 
[código de salida: 0]
```

```text
$ docker compose ps -a
NAME                         IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                      PORTS
s04_santana_derek-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    23 seconds ago   Exited (137) 1 second ago   
s04_santana_derek-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     23 seconds ago   Exited (0) 2 seconds ago    
s04_santana_derek-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        23 seconds ago   Exited (0) 1 second ago     
s04_santana_derek-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       23 seconds ago   Exited (0) 2 seconds ago    
[código de salida: 0]
```

```text
$ docker compose start
 Container s04_santana_derek-broker-1 Starting 
 Container s04_santana_derek-cache-1 Starting 
 Container s04_santana_derek-db-1 Starting 
 Container s04_santana_derek-broker-1 Started 
 Container s04_santana_derek-cache-1 Started 
 Container s04_santana_derek-db-1 Started 
 Container s04_santana_derek-db-1 Waiting 
 Container s04_santana_derek-db-1 Healthy 
 Container s04_santana_derek-web-1 Starting 
 Container s04_santana_derek-web-1 Started 
[código de salida: 0]
```

```text
$ docker compose exec -T db psql -U postgres -d banco -c 'SELECT * FROM cuentas;'
 id | saldo 
----+-------
  1 |   100
(1 row)

[código de salida: 0]
```

```text
$ docker compose down
 Container s04_santana_derek-broker-1 Stopping 
 Container s04_santana_derek-cache-1 Stopping 
 Container s04_santana_derek-web-1 Stopping 
 Container s04_santana_derek-web-1 Stopped 
 Container s04_santana_derek-web-1 Removing 
 Container s04_santana_derek-cache-1 Stopped 
 Container s04_santana_derek-cache-1 Removing 
 Container s04_santana_derek-web-1 Removed 
 Container s04_santana_derek-db-1 Stopping 
 Container s04_santana_derek-cache-1 Removed 
 Container s04_santana_derek-db-1 Stopped 
 Container s04_santana_derek-db-1 Removing 
 Container s04_santana_derek-broker-1 Stopped 
 Container s04_santana_derek-broker-1 Removing 
 Container s04_santana_derek-db-1 Removed 
 Container s04_santana_derek-broker-1 Removed 
 Network s04_santana_derek_default Removing 
 Network s04_santana_derek_default Removed 
[código de salida: 0]
```

```text
$ docker volume inspect s04_santana_derek_datos_banco --format '{{.Name}}'
s04_santana_derek_datos_banco
[código de salida: 0]
```

```text
$ docker compose up -d --wait --wait-timeout 120
 Network s04_santana_derek_default Creating 
 Network s04_santana_derek_default Created 
 Container s04_santana_derek-broker-1 Creating 
 Container s04_santana_derek-db-1 Creating 
 Container s04_santana_derek-cache-1 Creating 
 Container s04_santana_derek-cache-1 Created 
 Container s04_santana_derek-db-1 Created 
 Container s04_santana_derek-web-1 Creating 
 Container s04_santana_derek-broker-1 Created 
 Container s04_santana_derek-web-1 Created 
 Container s04_santana_derek-broker-1 Starting 
 Container s04_santana_derek-db-1 Starting 
 Container s04_santana_derek-cache-1 Starting 
 Container s04_santana_derek-broker-1 Started 
 Container s04_santana_derek-db-1 Started 
 Container s04_santana_derek-db-1 Waiting 
 Container s04_santana_derek-cache-1 Started 
 Container s04_santana_derek-db-1 Healthy 
 Container s04_santana_derek-web-1 Starting 
 Container s04_santana_derek-web-1 Started 
 Container s04_santana_derek-web-1 Waiting 
 Container s04_santana_derek-db-1 Waiting 
 Container s04_santana_derek-cache-1 Waiting 
 Container s04_santana_derek-broker-1 Waiting 
 Container s04_santana_derek-db-1 Healthy 
 Container s04_santana_derek-broker-1 Healthy 
 Container s04_santana_derek-cache-1 Healthy 
 Container s04_santana_derek-web-1 Healthy 
[código de salida: 0]
```

```text
$ docker compose exec -T db psql -U postgres -d banco -c 'SELECT * FROM cuentas;'
 id | saldo 
----+-------
  1 |   100
(1 row)

[código de salida: 0]
```

```text
$ docker compose down -v
 Container s04_santana_derek-web-1 Stopping 
 Container s04_santana_derek-cache-1 Stopping 
 Container s04_santana_derek-broker-1 Stopping 
 Container s04_santana_derek-web-1 Stopped 
 Container s04_santana_derek-web-1 Removing 
 Container s04_santana_derek-cache-1 Stopped 
 Container s04_santana_derek-cache-1 Removing 
 Container s04_santana_derek-web-1 Removed 
 Container s04_santana_derek-db-1 Stopping 
 Container s04_santana_derek-cache-1 Removed 
 Container s04_santana_derek-db-1 Stopped 
 Container s04_santana_derek-db-1 Removing 
 Container s04_santana_derek-db-1 Removed 
 Container s04_santana_derek-broker-1 Stopped 
 Container s04_santana_derek-broker-1 Removing 
 Container s04_santana_derek-broker-1 Removed 
 Network s04_santana_derek_default Removing 
 Volume s04_santana_derek_datos_banco Removing 
 Volume s04_santana_derek_datos_banco Removed 
 Network s04_santana_derek_default Removed 
[código de salida: 0]
```

```text
$ docker volume inspect s04_santana_derek_datos_banco
[]
Error response from daemon: get s04_santana_derek_datos_banco: no such volume
[código de salida: 1]
```

```text
$ docker compose up -d --wait --wait-timeout 120
 Network s04_santana_derek_default Creating 
 Network s04_santana_derek_default Created 
 Volume s04_santana_derek_datos_banco Creating 
 Volume s04_santana_derek_datos_banco Created 
 Container s04_santana_derek-broker-1 Creating 
 Container s04_santana_derek-db-1 Creating 
 Container s04_santana_derek-cache-1 Creating 
 Container s04_santana_derek-cache-1 Created 
 Container s04_santana_derek-db-1 Created 
 Container s04_santana_derek-web-1 Creating 
 Container s04_santana_derek-broker-1 Created 
 Container s04_santana_derek-web-1 Created 
 Container s04_santana_derek-cache-1 Starting 
 Container s04_santana_derek-db-1 Starting 
 Container s04_santana_derek-broker-1 Starting 
 Container s04_santana_derek-cache-1 Started 
 Container s04_santana_derek-broker-1 Started 
 Container s04_santana_derek-db-1 Started 
 Container s04_santana_derek-db-1 Waiting 
 Container s04_santana_derek-db-1 Healthy 
 Container s04_santana_derek-web-1 Starting 
 Container s04_santana_derek-web-1 Started 
 Container s04_santana_derek-db-1 Waiting 
 Container s04_santana_derek-cache-1 Waiting 
 Container s04_santana_derek-broker-1 Waiting 
 Container s04_santana_derek-web-1 Waiting 
 Container s04_santana_derek-db-1 Healthy 
 Container s04_santana_derek-broker-1 Healthy 
 Container s04_santana_derek-web-1 Healthy 
 Container s04_santana_derek-cache-1 Healthy 
[código de salida: 0]
```

```text
$ docker compose exec -T db psql -U postgres -d banco -c 'SELECT * FROM cuentas;'
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
[código de salida: 1]
```

```text
$ docker compose exec -T db psql -U postgres -d banco -c 'CREATE TABLE cuentas(id int primary key, saldo int); INSERT INTO cuentas VALUES (1,100);'
CREATE TABLE
INSERT 0 1
[código de salida: 0]
```

```text
$ docker compose ps
NAME                         IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04_santana_derek-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    23 seconds ago   Up 21 seconds             127.0.0.1:15674->15672/tcp
s04_santana_derek-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     23 seconds ago   Up 21 seconds             6379/tcp
s04_santana_derek-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        23 seconds ago   Up 21 seconds (healthy)   5432/tcp
s04_santana_derek-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       22 seconds ago   Up 2 seconds              127.0.0.1:8084->80/tcp
[código de salida: 0]
```


## HTTP y diagnóstico de los cuatro servicios

```text
$ curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:8084
200
```

```text
$ curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:15674
200
```

```text
$ docker compose exec -T broker rabbitmq-diagnostics -q ping
Ping succeeded
[código de salida: 0]
```

```text
$ docker compose exec -T web ls /usr/share/nginx/html
50x.html
index.html
[código de salida: 0]
```

```text
$ docker compose logs --tail 8 db
db-1  | 2026-10-04 13:15:10.581 UTC [1] LOG:  starting PostgreSQL 17.11 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
db-1  | 2026-10-04 13:15:10.581 UTC [1] LOG:  listening on IPv4 address "0.0.0.0", port 5432
db-1  | 2026-10-04 13:15:10.581 UTC [1] LOG:  listening on IPv6 address "::", port 5432
db-1  | 2026-10-04 13:15:10.591 UTC [1] LOG:  listening on Unix socket "/var/run/postgresql/.s.PGSQL.5432"
db-1  | 2026-10-04 13:15:10.600 UTC [68] LOG:  database system was shut down at 2026-10-04 13:15:10 UTC
db-1  | 2026-10-04 13:15:10.618 UTC [1] LOG:  database system is ready to accept connections
db-1  | 2026-10-04 13:15:17.490 UTC [85] ERROR:  relation "cuentas" does not exist at character 15
db-1  | 2026-10-04 13:15:17.490 UTC [85] STATEMENT:  SELECT * FROM cuentas;
[código de salida: 0]
```


## Servicios anteriores conservados

```text
$ docker ps --format '{{.ID}} {{.Names}} {{.Ports}}'
6360ee942d34 s04_santana_derek-web-1 127.0.0.1:8084->80/tcp
ac2e65d50110 s04_santana_derek-db-1 5432/tcp
cd6c43c66200 s04_santana_derek-broker-1 127.0.0.1:15674->15672/tcp
3bc4a4e41e7d s04_santana_derek-cache-1 6379/tcp
4c23f7a760ed opcional-idempotencia-cobros-1 127.0.0.1:8083->8080/tcp
b8e00b17320e derek-s02-sitio 0.0.0.0:9090->80/tcp, [::]:9090->80/tcp
c1ff3fd97a51 derek-s02-sitio2 0.0.0.0:9091->80/tcp, [::]:9091->80/tcp
[código de salida: 0]
```

## Comprobación guiada y entrega

El sistema queda levantado con una fila `(1,100)` de práctica. Además del volumen Compose, se conserva `s04_santana_derek_m1` para la evidencia de persistencia. Los contenedores auxiliares de las misiones fueron eliminados; no se realizó ninguna limpieza global.

En la copia habitual se incluye `comprobar.py` como ayuda **local e ignorada por Git**. Se ejecuta con `python3 comprobar.py` desde S04; no elimina datos ni cambia archivos. Revisa sus resultados por orden: rama, archivos ignorados, servicios saludables, fila 100, PONG, conexión dentro de la red, fallo esperado fuera de ella y HTTP 200. Si el sistema estaba apagado, primero usa `docker compose up -d --wait`. La fila pertenece al volumen de esta ejecución; una reproducción desde cero debe crear la tabla e insertar la fila con los comandos registrados arriba antes de esa comprobación.

La revisión final del alumno consiste en ejecutar la comprobación, leer estas respuestas y revisar GitHub Desktop. Deben aparecer exactamente cuatro archivos para entregar: `compose.yaml`, `.env.example`, `.gitignore` y `evidencia.md`. No deben aparecer `.env`, `.env.prod` ni `comprobar.py`. Después hará el commit y **Push origin** en `entregas_santana_derek`. No abrir PR y no enviar a main.

No se realizó commit, push ni PR durante esta preparación. El alumno ya ejecutó personalmente la comprobación guiada y compartió los resultados correctos, registrados abajo. Quedan pendientes su revisión de los archivos en GitHub Desktop, el commit y Push origin.

Comprobación de incorporación: la copia habitual estaba limpia en `entregas_santana_derek`, HEAD `524036da47ded01df9a18130f045ad91012d7c1c`. Se compararon 25 archivos de S02/S03 mediante SHA-256 contra la copia inicial y este worktree: todos idénticos. Solo se incorpora S04, sin sobrescribir una carpeta existente.

Después de copiar, se ejecutó `python3 comprobar.py` desde la copia habitual: rama correcta, tres archivos locales ignorados, cuatro servicios arriba con db healthy, fila 100, PONG, conexión por nombre dentro de la red, fallo esperado fuera y HTTP 200 en ambos puertos. Todos pasaron. `git status --short --untracked-files=all` mostró únicamente los cuatro archivos requeridos de S04, sin cambios en archivos anteriores.


## Comprobación personal del alumno

Derek ejecutó personalmente el siguiente comando en su checkout habitual y compartió la salida correcta. Este registro corresponde a los resultados proporcionados por el alumno; no es una nueva ejecución automatizada. No se registran hora ni duraciones porque no fueron proporcionadas.

```bash
python3 ~/Documents/sd-2027-1-derek/entregas/santana_derek/s04/comprobar.py
```

Salida del alumno, resumida con los valores que compartió (se omiten duraciones):

```text
1. Rama personal
entregas_santana_derek
OK

2. Archivos locales excluidos de Git
.env
.env.prod
comprobar.py
OK

3. Cuatro servicios; db debe decir healthy
Proyecto: s04_santana_derek
broker  rabbitmq:4-management-alpine  Up            127.0.0.1:15674->15672/tcp
cache   redis:8-alpine                Up            6379/tcp (interno)
db      postgres:17-alpine            Up (healthy)  5432/tcp (interno)
web     nginx:alpine                  Up            127.0.0.1:8084->80/tcp
OK

4. Fila guardada en PostgreSQL
id | saldo
1  | 100
(1 row)
OK

5. Redis responde PONG
PONG
OK

6. db responde por nombre en la red S04
db:5432 - accepting connections
OK

7. Fuera de la red falla, como debe
db:5432 - no response
OK

8. HTTP en puerto 8084: 200 OK
8. HTTP en puerto 15674: 200 OK

Comprobación correcta.
```

El alumno confirmó así la rama personal, la exclusión de los archivos locales, los cuatro servicios activos, PostgreSQL saludable y su fila de prueba, Redis, la conexión por nombre dentro de la red, el fallo esperado fuera de ella y las dos respuestas HTTP. La comprobación guiada está realizada; el commit y push siguen a cargo del alumno en GitHub Desktop, sin PR durante el semestre.
