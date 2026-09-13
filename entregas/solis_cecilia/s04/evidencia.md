# Evidencia de Laboratorio · S04
**Nombre:** Cecilia Ximena Solís Cisneros  
**Fecha:** 12 de septiembre de 2026  

---

## Misión 1 · Persistencia

Demostración de que el estado reside en el volumen y no en la capa del contenedor.

### 1. Sin volumen (los datos se pierden al recrear el contenedor)

<pre><code>$ docker run -d --name db_sin_vol -e POSTGRES_PASSWORD=secreto postgres:17-alpine
d5aa808d7146bc39f6e05b8c727275d3bff7688e2610bf011e6f2b11c2bdd7c0

$ docker exec db_sin_vol psql -U postgres -c "CREATE TABLE cuentas (id int, saldo numeric); INSERT INTO cuentas VALUES (1, 600.0);"
CREATE TABLE
INSERT 0 1

$ docker rm -f db_sin_vol
db_sin_vol

$ docker run -d --name db_sin_vol -e POSTGRES_PASSWORD=secreto postgres:17-alpine
ce69dddf5ad57a3fdb08ca9ff15aa200e6e321f1bf7fabbe2ef9ed11977042ea

$ docker exec db_sin_vol psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^

$ docker rm -f db_sin_vol
db_sin_vol</code></pre>

### 2. Con volumen nombrado (los datos sobreviven al recrear el contenedor)

<pre><code>$ docker run -d --name db_con_vol -v vol_m1:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
14e3f04e80d0b85e906bee92c241eedb09305894b50eebd2dc51268d53d28cc2

$ docker exec db_con_vol psql -U postgres -c "CREATE TABLE cuentas (id int, saldo numeric); INSERT INTO cuentas VALUES (1, 600.0);"
CREATE TABLE
INSERT 0 1

$ docker rm -f db_con_vol
db_con_vol

$ docker run -d --name db_con_vol -v vol_m1:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
8963a13b6e2b8e12195f3fa3ab39a085027968a07d80e9ba738a9887f428b436

$ docker exec db_con_vol psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo 
----+-------
  1 | 600.0
(1 row)

$ docker rm -f db_con_vol
db_con_vol</code></pre>

---

## Misión 2 · Red

El nombre del contenedor funciona como dirección solo dentro de una red definida por el usuario.

### 1. Dentro de la red s04_default

<pre><code>$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections</code></pre>

### 2. Fuera de la red (red bridge por defecto)

<pre><code>$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response</code></pre>

---

## Misión 3 · Configuración

Demostración de desacoplamiento de configuración (Factor III). La misma imagen `postgres:17-alpine` levantada con dos archivos de entorno diferentes:

### 1. Entorno de desarrollo (.env)

<pre><code>$ docker exec db_app_dev env | grep POSTGRES
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco</code></pre>

### 2. Entorno de producción (.env.prod)

<pre><code>$ docker exec db_app_prod env | grep POSTGRES
POSTGRES_DB=banco_sist_distribuidos_prod
POSTGRES_PASSWORD=pass_entrega_ceci_s04</code></pre>

---

## Misión 4 · Compose

### Salida de docker compose ps

<pre><code>NAME           IMAGE                          COMMAND                  SERVICE   CREATED       STATUS                    PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    4 hours ago   Up 4 hours                0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     4 hours ago   Up 4 hours                6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        4 hours ago   Up 4 hours (healthy)      5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       4 hours ago   Up 4 hours                0.0.0.0:8080->80/tcp, [::]:8080->80/tcp</code></pre>

### Preguntas teóricas

* **¿Qué se pierde con `down` y qué con `down -v`?**  
  Con `docker compose down` se detienen y eliminan los contenedores y la red virtual creada por Compose, pero el volumen nombrado (`datos_banco`) se conserva intacto en el host junto con todos sus datos. En cambio, `docker compose down -v` elimina los contenedores, la red y además destruye físicamente los volúmenes, perdiendo la información almacenada de manera definitiva.

* **¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?**  
  Porque ambos servicios comparten la misma red interna creada por Compose (`s04_default`). En las redes definidas por el usuario, el DNS integrado de Docker permite la comunicación interna entre contenedores resolviendo directamente por nombre de servicio (`db`) en cualquier puerto que el contenedor exponga. La directiva `ports` solo se requiere cuando se necesita exponer un puerto hacia la máquina anfitriona (host).

* **¿Qué pasaría si el `.env` estuviera dentro de la imagen?**  
  Se rompería el principio de separación entre código y configuración (Factor III de *12-Factor App*). La imagen dejaría de ser inmutable y portable, obligando a reconstruirla por completo cada vez que cambie una credencial o entorno. Además, representaría una vulnerabilidad crítica de seguridad al exponer secretos y contraseñas a cualquiera que tenga acceso a la imagen o al repositorio.