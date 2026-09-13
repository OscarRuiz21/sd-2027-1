# S04: Docker — Amy Veraza

## Misión 1: Persistencia
Salidas reales; contraseñas ocultadas.

```text
> docker run -d --name s04_prueba_sin --env-file .env postgres:17-alpine
137907a7e81f8387ec906d43d81b0adf38e355283bd453eec933021075b44d84
```


```text
> docker exec s04_prueba_sin psql -U postgres -c CREATE TABLE cuentas(id int primary key, saldo int); INSERT INTO cuentas VALUES (1,100); SELECT * FROM cuentas;
CREATE TABLE
INSERT 0 1
 id | saldo
----+-------
  1 |   100
(1 row)
```


```text
> docker rm -f s04_prueba_sin
s04_prueba_sin
```


```text
> docker run -d --name s04_prueba_sin --env-file .env postgres:17-alpine
1eca635930c9984c38b7095941496a4a528b76d6c3ff6342caab9b1cacd998a6
```


```text
> docker exec s04_prueba_sin psql -U postgres -c SELECT * FROM cuentas;
docker.exe : ERROR:  relation "cuentas" does not exist
En C:\Users\AmyVa\OneDrive\Documentos\Sistemas Distribuidos\run-s04.ps1: 4 Carácter: 13
+   $output = & docker @DockerArgs 2>&1
+             ~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : NotSpecified: (ERROR:  relatio... does not exist:String) [], RemoteException
    + FullyQualifiedErrorId : NativeCommandError

LINE 1: SELECT * FROM cuentas;
                      ^
```


```text
> docker run -d --name s04_prueba_con --env-file .env -v s04_prueba_datos:/var/lib/postgresql/data postgres:17-alpine
d7d2117018cd0c71a977a95dc71f170c86696c4d8e1f9607939dd9656df3cdd6
```


```text
> docker exec s04_prueba_con psql -U postgres -c CREATE TABLE cuentas(id int primary key, saldo int); INSERT INTO cuentas VALUES (1,100); SELECT * FROM cuentas;
CREATE TABLE
INSERT 0 1
 id | saldo
----+-------
  1 |   100
(1 row)
```


```text
> docker rm -f s04_prueba_con
s04_prueba_con
```


```text
> docker run -d --name s04_prueba_con --env-file .env -v s04_prueba_datos:/var/lib/postgresql/data postgres:17-alpine
a5ef1931baad3c3fec37ea0d7e0616e99e066c12e3d68a8aae8ffd98d74d27c6
```


```text
> docker exec s04_prueba_con psql -U postgres -c SELECT * FROM cuentas;
 id | saldo
----+-------
  1 |   100
(1 row)
```

La fila sobrevive cuando se reutiliza el volumen nombrado. Sin -v, esta imagen de Postgres crea un volumen anónimo: el contenedor nuevo recibe otro y no reutiliza los datos anteriores automáticamente.

## Misión 2: Red

```text
> docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
```


```text
> docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
```


## Misión 3: Configuración

```text
> docker run -d --name s04_prueba_dev --env-file .env postgres:17-alpine
61fbb7811cb0672a520f39a0242bc8be9eaf3050dff6322925123f20af49ed6a
```


```text
> docker run -d --name s04_prueba_prod --env-file .env.prod postgres:17-alpine
7430a96546159fb2b60e13a45abc68e34f3e81fd2cac3faa382a57a42acfe14f
```


```text
> docker exec s04_prueba_dev env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=61fbb7811cb0
POSTGRES_PASSWORD=[OCULTA]
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


```text
> docker exec s04_prueba_prod env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=7430a9654615
POSTGRES_PASSWORD=[OCULTA]
POSTGRES_DB=banco_prod
GOSU_VERSION=1.19
LANG=en_US.utf8
PG_MAJOR=17
PG_VERSION=17.11
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979
DOCKER_PG_LLVM_DEPS=llvm21-dev 		clang21
PGDATA=/var/lib/postgresql/data
HOME=/root
```

La misma imagen recibe POSTGRES_DB=banco y POSTGRES_DB=banco_prod desde archivos distintos. Las contraseñas se sustituyeron por [OCULTA]. Los dos archivos privados están ignorados por Git; .env.example contiene valores de ejemplo.

## Misión 4: Compose

```text
> docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED              STATUS                        PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    About a minute ago   Up About a minute             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     About a minute ago   Up About a minute             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        About a minute ago   Up About a minute (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       24 seconds ago       Up 23 seconds                 0.0.0.0:8081->80/tcp, [::]:8081->80/tcp
```


```text
> docker compose exec -T db psql -U postgres -c \l
                                                    List of databases
   Name    |  Owner   | Encoding | Locale Provider |  Collate   |   Ctype    | Locale | ICU Rules |   Access privileges
-----------+----------+----------+-----------------+------------+------------+--------+-----------+-----------------------
 banco     | postgres | UTF8     | libc            | en_US.utf8 | en_US.utf8 |        |           |
 postgres  | postgres | UTF8     | libc            | en_US.utf8 | en_US.utf8 |        |           |
 template0 | postgres | UTF8     | libc            | en_US.utf8 | en_US.utf8 |        |           | =c/postgres          +
           |          |          |                 |            |            |        |           | postgres=CTc/postgres
 template1 | postgres | UTF8     | libc            | en_US.utf8 | en_US.utf8 |        |           | =c/postgres          +
           |          |          |                 |            |            |        |           | postgres=CTc/postgres
(4 rows)
```


```text
> docker compose exec -T cache redis-cli ping
PONG
```


```text
> docker compose exec -T web wget -qO- http://localhost
<!DOCTYPE html>
<html>
<head>
<title>Welcome to nginx!</title>
<style>
html { color-scheme: light dark; }
body { width: 35em; margin: 0 auto;
font-family: Tahoma, Verdana, Arial, sans-serif; }
</style>
</head>
<body>
<h1>Welcome to nginx!</h1>
<p>If you see this page, nginx is successfully installed and working.
Further configuration is required for the web server, reverse proxy,
API gateway, load balancer, content cache, or other features.</p>

<p>For online documentation and support please refer to
<a href="https://nginx.org/">nginx.org</a>.<br/>
To engage with the community please visit
<a href="https://community.nginx.org/">community.nginx.org</a>.<br/>
For enterprise grade support, professional services, additional
security features and capabilities please refer to
<a href="https://f5.com/nginx">f5.com/nginx</a>.</p>

<p><em>Thank you for using nginx.</em></p>
</body>
</html>
```


```text
> docker compose exec -T broker rabbitmq-diagnostics -q ping
Ping succeeded
```


```text
> docker network inspect s04_default --format {{range .Containers}}{{println .Name .IPv4Address}}{{end}}
s04-db-1 172.19.0.3/16
s04-web-1 172.19.0.5/16
s04-cache-1 172.19.0.2/16
s04-broker-1 172.19.0.4/16
```


### ¿Qué se pierde con down y qué con down -v?
`docker compose down` elimina los contenedores y la red del proyecto; se pierde lo escrito en las capas de los contenedores, pero el volumen nombrado de Postgres se conserva. `docker compose down -v` también elimina los volúmenes nombrados declarados y los anónimos adjuntos: en este proyecto se pierden los datos de Postgres. Los volúmenes externos no se eliminan. Redis y RabbitMQ no tienen persistencia configurada aquí.

### ¿Por qué web alcanza a db sin publicar el puerto 5432?
Ambos pertenecen a la red s04_default. El DNS de Docker resuelve el nombre de servicio db y permite conectarse a su puerto interno 5432. Publicar un puerto permite acceder desde fuera de esa red; no es necesario para la comunicación entre sus contenedores.

### ¿Qué pasaría si el .env estuviera dentro de la imagen?
Las credenciales quedarían distribuidas con la imagen y podrían extraerse. Cambiar la configuración incorporada exigiría reconstruirla; se dificultaría reutilizar la misma imagen en distintos entornos. Copiar el archivo tampoco carga automáticamente sus variables: algún proceso tendría que leerlo.

## Reproducción
Desde esta carpeta, copiar .env.example a .env, sustituir la contraseña de ejemplo y ejecutar `docker compose up -d`. Verificar con `docker compose ps`. nginx: http://localhost:8081. RabbitMQ: http://localhost:15672 (guest / guest para este laboratorio local).

Nota del entorno: se publicó nginx en el puerto 8081 porque 8080 ya estaba ocupado. Las dos páginas locales respondieron HTTP 200.
