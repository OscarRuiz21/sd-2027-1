### Persistencia
```shell
docker exec -it pg-sin-vol psql -U postgres -c "SELECT * FROM prueba;"
ERROR:  relation "prueba" does not exist
LINE 1: SELECT * FROM prueba;
                      ^
```

``` shell
docker exec -it pg-con-vol psql -U postgres -c "SELECT * FROM prueba;"
    val    
-----------
 sobrevive
(1 row)
```
### Red
```shell
docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
```

```shell
docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
```
### Configuración
```bash
docker exec app-dev env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=be7ea447fe49
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
GOSU_VERSION=1.19
LANG=en_US.utf8
PG_MAJOR=17
PG_VERSION=17.11
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979
DOCKER_PG_LLVM_DEPS=llvm21-dev 		clang21
PGDATA=/var/lib/postgresql/data
HOME=/root
```

```bash
docker exec app-prod env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=c9118a646d0f
POSTGRES_PASSWORD=password_produccion_999
GOSU_VERSION=1.19
LANG=en_US.utf8
PG_MAJOR=17
PG_VERSION=17.11
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979
DOCKER_PG_LLVM_DEPS=llvm21-dev 		clang21
PGDATA=/var/lib/postgresql/data
HOME=/root
```
### Compose
```shell
docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED             STATUS                       PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    About an hour ago   Up About an hour             4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     About an hour ago   Up About an hour             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        About an hour ago   Up About an hour (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       About an hour ago   Up About an hour             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

#### 1.  ¿Qué se pierde con `down` y qué con `down -v`? 
`docker compose down` detine y elimina los contenedores y las redes creadas, pero respeta y conserva los volúmnes nombrados. `docker compose down -v` hace lo mismo, pero elimina también los volúmenes asociados; borrará todo el contenido persistente de la bd o servicios, por lo que se perderá la información guardada.
#### 2. ¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?
Porque pertenecen a la misma red virtual de Docker (`s04_default`). Docker provee un servidor DNS interno donde los nombres de los contenedores se resuelven automáticamente entre sí dentro de esa red. EL mapeo de puertos con `ports` solo es necesario para exponer servicios hacia la máquina anfitriona, no para la comunicación interna entre contenedores de una misma red.
#### 3. ¿Qué pasaría si el `.env` estuviera dentro de la imagen?
Las credenciales, contraseñas y configuraciones sensibles quedarían quemadas dentro del código o empaquetada en la imagen, lo que representaría una falla crítica de seguridad si la imagen se hace pública, además de impedir reutilizarla para entornos distintos. También estaríamos rompiendo el *Factor III* de la metodología de aplicaciones de **12 Factores**.

##### By Pérez Paitán Brent
