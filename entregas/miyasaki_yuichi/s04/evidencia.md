# Evidencia S04:

## Misión 1 Persistencia

```
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec -it m1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug m1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec -it m1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug m1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker rm -f m1
m1
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker run -d --name m1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
a313bf797a08e13364eada4a5ff2cead294c057831c4291d7680c1c3982e5c2d
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec -it m1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug m1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker rm -f m1
m1
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker run -d --name m1 -v datos_m1:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
76e184534b3cb6b9f83d5cc88d6caba3e7893cc2738f58db9f6b1956cde2eb3f
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec -it m1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug m1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec -it m1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug m1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker rm -f m1
m1
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker run -d --name m1 -v datos_m1:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
f9ee8d2499db6944062a01f70bbfde9131322772bf52b6b974e3fdbcf946c1db
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec -it m1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo 
----+-------
  1 |   100
(1 row)


What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug m1
    Learn more at https://docs.docker.com/go/debug-cli/
```

**Conclusión:** Sin volumen, al borrar el contenedor con `docker rm -f` y recrearlo, Postgres inicializa una carpeta de datos nueva y vacía, por lo que la tabla `cuentas` ya no existe. Con un volumen nombrado, los archivos de la base viven fuera de la capa de escritura del contenedor; al borrar y recrear el contenedor con el mismo volumen, la fila `1 | 100` sigue ahí, esto confirma que el estado vive en el volumen, no en el contenedor.

## Misión 2 Red

```
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
```

**Conclusión:** Dentro de la red `s04_default`, `pg_isready -h db` responde `db:5432 - accepting connections`, el contenedor `db` se resuelve por nombre porque Compose lo registró en el DNS interno de esa red. Fuera de la red (sin `--network`), el contenedor cae en la red `bridge` por defecto, que no tiene DNS, y el comando responde `db:5432 - no response`, y esto confirma que el nombre solo es una dirección válida dentro de la red donde vive el contenedor.


## Misión 3 Configuración

```
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec db_e1 env | Select-String POSTGRES

POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
PGDATA=/var/lib/postgresql/data


PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec db_e2 env | Select-String POSTGRES

POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
PGDATA=/var/lib/postgresql/data
```

**Conclusión:** Se levantó la misma imagen `postgres:17-alpine` dos veces, cada una con un archivo de variables distinto. `docker exec ... env | Select-String POSTGRES` muestra que cada contenedor solo ve su propia configuración, aunque la imagen es idéntica en ambos casos. Esto es el Factor III de 12-factor: la configuración vive fuera de la imagen, en el entorno. En el repo, `.env` está listado en `.gitignore` para no subir credenciales, y `.env.example` documenta los nombres de las variables con valores falsos.


## Misión 4 Compose

```
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker compose up -d
[+] up 4/4
 ✔ Container s04-web-1    Running                                                                     0.0s
 ✔ Container s04-db-1     Healthy                                                                     0.5s
 ✔ Container s04-cache-1  Running                                                                     0.0s
 ✔ Container s04-broker-1 Running                                                                     0.0s

```

**Preguntas:**

¿Qué se pierde con `down` y qué con `down -v`? `docker compose down` borra los contenedores y la red del proyecto, pero conserva el volumen `datos_banco`: los datos sobreviven. `docker compose down -v` borra también el volumen, así que los datos se pierden.

¿Por qué `web` alcanza a `db` sin publicar el puerto 5432? Porque ambos están en la misma red interna que crea Compose (`s04_default`); ahí se resuelven por nombre de servicio directamente sobre el puerto del contenedor. Publicar un puerto solo expone el servicio hacia fuera del host, no es necesario para la comunicación entre contenedores de la misma red.

¿Qué pasaría si el `.env` estuviera dentro de la imagen? Se rompería el Factor III: la contraseña quedaría horneada en la imagen y visible en su historial de capas, no podrías reutilizar la misma imagen para dev/prod sin reconstruirla, y si la imagen se publicara se filtraría la credencial.

**`docker compose config` (con la contraseña como ******):**

```
name: s04
services:
  broker:
    image: rabbitmq:4-management-alpine
    networks:
      default: null
    ports:
      - mode: ingress
        target: 15672
        published: "15672"
        protocol: tcp
  cache:
    image: redis:8-alpine
    networks:
      default: null
  db:
    environment:
      POSTGRES_DB: banco
      POSTGRES_PASSWORD: ******
    healthcheck:
      test:
        - CMD-SHELL
        - pg_isready -U postgres
      timeout: 3s
      interval: 5s
      retries: 5
    image: postgres:17-alpine
    networks:
      default: null
    volumes:
      - type: volume
        source: datos_banco
        target: /var/lib/postgresql/data
        volume: {}
  web:
    depends_on:
      db:
        condition: service_healthy
        required: true
    image: nginx:alpine
    networks:
      default: null
    ports:
      - mode: ingress
        target: 80
        published: "8080"
        protocol: tcp
networks:
  default:
    name: s04_default
volumes:
  datos_banco:
    name: s04_datos_banco
```


## Evidencia Paso a Paso del Laboratorio

```
PS C:\WINDOWS\System32> docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
4cd7997ce968fb7d8e2b8b97eb165cd96f43ac5afdd17c30585e6169ad30cd35
PS C:\WINDOWS\System32> docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\WINDOWS\System32> docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\WINDOWS\System32> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)


What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\WINDOWS\System32> docker rm -f db1
db1
PS C:\WINDOWS\System32> docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
7ff22447d691fc766398ec619c93eb22c75475a0948ba0603e31bf3ddd3e1295
PS C:\WINDOWS\System32> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\WINDOWS\System32> docker rm -f db1
db1
PS C:\WINDOWS\System32> docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
e2fa8203337d15159de5824ff5515e34885103770ccffde4c4a82bd1a123ba44
PS C:\WINDOWS\System32> docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\WINDOWS\System32> docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\WINDOWS\System32> docker rm -f db1
db1
PS C:\WINDOWS\System32> docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
0cc2ced38d06ef5fca5db71af4ab4fa753b8cb4e3340aa98efdd470e243c3228
PS C:\WINDOWS\System32> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)


What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\WINDOWS\System32> docker volume ls
DRIVER    VOLUME NAME
local     6691e86e03e273d0d301a718b64ef9e20d1b2cd20f09c597de016d7e2258fa76
local     3656574d1cdf0ae61142e6714abc95871d76b0d614abddbb225c47b8afa35cd6
local     datos_banco
PS C:\WINDOWS\System32> docker volume inspect datos_banco
[
    {
        "CreatedAt": "2026-09-12T13:43:01Z",
        "Driver": "local",
        "Labels": null,
        "Mountpoint": "/var/lib/docker/volumes/datos_banco/_data",
        "Name": "datos_banco",
        "Options": null,
        "Scope": "local"
    }
]
PS C:\WINDOWS\System32> docker network create redlab
0b5e53b8fb8ceff57805b178d19faafcebb6ed12e3b5b143cb97195dd75a62ea
PS C:\WINDOWS\System32> docker run -d --name web --network redlab nginx:alpine
d854ad7f119ee5de79f3252d77196e546c18a6838657d0a4c0f887402fadb9bd
PS C:\WINDOWS\System32> docker run --rm --network redlab alpine:3.20 wget -qO- http://web
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
PS C:\WINDOWS\System32> docker run --rm alpine:3.20 wget -qO- http://web
wget: bad address 'web'

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
PS C:\WINDOWS\System32> docker network inspect redlab
[
    {
        "Name": "redlab",
        "Id": "0b5e53b8fb8ceff57805b178d19faafcebb6ed12e3b5b143cb97195dd75a62ea",
        "Created": "2026-09-12T13:47:31.064137547Z",
        "Scope": "local",
        "Driver": "bridge",
        "EnableIPv4": true,
        "EnableIPv6": false,
        "IPAM": {
            "Driver": "default",
            "Options": {},
            "Config": [
                {
                    "Subnet": "172.18.0.0/16",
                    "Gateway": "172.18.0.1"
                }
            ]
        },
        "Internal": false,
        "Attachable": false,
        "Ingress": false,
        "ConfigFrom": {
            "Network": ""
        },
        "ConfigOnly": false,
        "Options": {
            "com.docker.network.enable_ipv4": "true",
            "com.docker.network.enable_ipv6": "false"
        },
        "Labels": {},
        "Containers": {
            "d854ad7f119ee5de79f3252d77196e546c18a6838657d0a4c0f887402fadb9bd": {
                "Name": "web",
                "EndpointID": "9bc66945e6840467b2f5693e07e8967eb0ec406baf81db1dd9c0bf5db5ac5da9",
                "MacAddress": "1a:15:10:8d:c6:f5",
                "IPv4Address": "172.18.0.2/16",
                "IPv6Address": ""
            }
        },
        "Status": {
            "IPAM": {
                "Subnets": {
                    "172.18.0.0/16": {
                        "IPsInUse": 4,
                        "DynamicIPsAvailable": 65532
                    }
                }
            }
        }
    }
]
PS C:\WINDOWS\System32> docker run -d --name db_dev --network redlab -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=banco_dev postgres:17-alpine
60aca24b7ce7b2a7ddadf1f330755caf587b19148c67414727c7eaba464a4c3c
PS C:\WINDOWS\System32> docker run -d --name db_prod --network redlab -e POSTGRES_PASSWORD=prod -e POSTGRES_DB=banco_prod postgres:17-alpine
8c816c11e00adea87ff6fce11c14715ea68fe961ee97c183058500ba35bba37a

PS C:\WINDOWS\System32> docker exec db_dev env | Select-String POSTGRES

POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
PGDATA=/var/lib/postgresql/data


PS C:\WINDOWS\System32> docker exec db_prod env | Select-String POSTGRES

POSTGRES_DB=banco_prod
POSTGRES_PASSWORD=prod
PGDATA=/var/lib/postgresql/data


PS C:\WINDOWS\System32> docker exec db_dev psql -U postgres -c "\l" | Select-String banco

 banco_dev | postgres | UTF8     | libc            | en_US.utf8 | en_US.utf8 |        |
         |

PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker run --rm --env-file .env postgres:17-alpine env | Select-String POSTGRES

POSTGRES_PASSWORD=secreto
PGDATA=/var/lib/postgresql/data
POSTGRES_DB=banco

PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec db_dev env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=60aca24b7ce7
POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
GOSU_VERSION=1.19
LANG=en_US.utf8
PG_MAJOR=17
PG_VERSION=17.11
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979
DOCKER_PG_LLVM_DEPS=llvm21-dev          clang21
PGDATA=/var/lib/postgresql/data
HOME=/root
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker exec db_prod env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=8c816c11e00a
POSTGRES_DB=banco_prod
POSTGRES_PASSWORD=prod
GOSU_VERSION=1.19
LANG=en_US.utf8
PG_MAJOR=17
PG_VERSION=17.11
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979
DOCKER_PG_LLVM_DEPS=llvm21-dev          clang21
PGDATA=/var/lib/postgresql/data
HOME=/root
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker compose up -d
[+] up 6/6
 ✔ Network s04_default    Created                                                                     0.0s
 ✔ Volume s04_datos_banco Created                                                                     0.0s
 ✔ Container s04-broker-1 Started                                                                     0.4s
 ✔ Container s04-cache-1  Started                                                                     0.3s
 ✔ Container s04-db-1     Healthy                                                                     5.8s
 ✔ Container s04-web-1    Started                                                                     5.8s
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    16 seconds ago   Up 15 seconds             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     16 seconds ago   Up 15 seconds             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        16 seconds ago   Up 15 seconds (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       16 seconds ago   Up 9 seconds              0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker compose logs db
db-1  | The files belonging to this database system will be owned by user "postgres".
db-1  | This user must also own the server process.
db-1  |
db-1  | The database cluster will be initialized with locale "en_US.utf8".
db-1  | The default database encoding has accordingly been set to "UTF8".
db-1  | The default text search configuration will be set to "english".
db-1  |
db-1  | Data page checksums are disabled.
db-1  |
db-1  | fixing permissions on existing directory /var/lib/postgresql/data ... ok
db-1  | creating subdirectories ... ok
db-1  | selecting dynamic shared memory implementation ... posix
db-1  | selecting default "max_connections" ... 100
db-1  | selecting default "shared_buffers" ... 128MB
db-1  | selecting default time zone ... UTC
db-1  | creating configuration files ... ok
db-1  | running bootstrap script ... ok
db-1  | sh: locale: not found
db-1  | 2026-09-12 14:27:39.395 UTC [35] WARNING:  no usable system locales were found
db-1  | performing post-bootstrap initialization ... ok
db-1  | initdb: warning: enabling "trust" authentication for local connections
db-1  | initdb: hint: You can change this by editing pg_hba.conf or using the option -A, or --auth-local and --auth-host, the next time you run initdb.
db-1  | syncing data to disk ... ok
db-1  |
db-1  |
db-1  | Success. You can now start the database server using:
db-1  |
db-1  |     pg_ctl -D /var/lib/postgresql/data -l logfile start
db-1  |
db-1  | waiting for server to start....2026-09-12 14:27:39.892 UTC [41] LOG:  starting PostgreSQL 17.11 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
db-1  | 2026-09-12 14:27:39.893 UTC [41] LOG:  listening on Unix socket "/var/run/postgresql/.s.PGSQL.5432"
db-1  | 2026-09-12 14:27:39.898 UTC [44] LOG:  database system was shut down at 2026-09-12 14:27:39 UTC
db-1  | 2026-09-12 14:27:39.903 UTC [41] LOG:  database system is ready to accept connections
db-1  |  done
db-1  | server started
db-1  | CREATE DATABASE
db-1  |
db-1  |
db-1  | /usr/local/bin/docker-entrypoint.sh: ignoring /docker-entrypoint-initdb.d/*
db-1  |
db-1  | waiting for server to shut down...2026-09-12 14:27:40.029 UTC [41] LOG:  received fast shutdown request
db-1  | .2026-09-12 14:27:40.030 UTC [41] LOG:  aborting any active transactions
db-1  | 2026-09-12 14:27:40.031 UTC [41] LOG:  background worker "logical replication launcher" (PID 47) exited with exit code 1
db-1  | 2026-09-12 14:27:40.031 UTC [42] LOG:  shutting down
db-1  | 2026-09-12 14:27:40.032 UTC [42] LOG:  checkpoint starting: shutdown immediate
db-1  | 2026-09-12 14:27:40.099 UTC [42] LOG:  checkpoint complete: wrote 926 buffers (5.7%); 0 WAL file(s) added, 0 removed, 0 recycled; write=0.018 s, sync=0.046 s, total=0.068 s; sync files=301, longest=0.006 s, average=0.001 s; distance=4266 kB, estimate=4266 kB; lsn=0/191E818, redo lsn=0/191E818
db-1  | 2026-09-12 14:27:40.105 UTC [41] LOG:  database system is shut down
db-1  |  done
db-1  | server stopped
db-1  |
db-1  | PostgreSQL init process complete; ready for start up.
db-1  |
db-1  | 2026-09-12 14:27:40.145 UTC [1] LOG:  starting PostgreSQL 17.11 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
db-1  | 2026-09-12 14:27:40.145 UTC [1] LOG:  listening on IPv4 address "0.0.0.0", port 5432
db-1  | 2026-09-12 14:27:40.145 UTC [1] LOG:  listening on IPv6 address "::", port 5432
db-1  | 2026-09-12 14:27:40.148 UTC [1] LOG:  listening on Unix socket "/var/run/postgresql/.s.PGSQL.5432"
db-1  | 2026-09-12 14:27:40.151 UTC [57] LOG:  database system was shut down at 2026-09-12 14:27:40 UTC
db-1  | 2026-09-12 14:27:40.155 UTC [1] LOG:  database system is ready to accept connections
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker compose exec db psql -U postgres -c "\l"
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

PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker compose exec cache redis-cli ping
PONG

PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker compose config
name: s04
services:
  broker:
    image: rabbitmq:4-management-alpine
    networks:
      default: null
    ports:
      - mode: ingress
        target: 15672
        published: "15672"
        protocol: tcp
  cache:
    image: redis:8-alpine
    networks:
      default: null
  db:
    environment:
      POSTGRES_DB: banco
      POSTGRES_PASSWORD: ******
    healthcheck:
      test:
        - CMD-SHELL
        - pg_isready -U postgres
      timeout: 3s
      interval: 5s
      retries: 5
    image: postgres:17-alpine
    networks:
      default: null
    volumes:
      - type: volume
        source: datos_banco
        target: /var/lib/postgresql/data
        volume: {}
  web:
    depends_on:
      db:
        condition: service_healthy
        required: true
    image: nginx:alpine
    networks:
      default: null
    ports:
      - mode: ingress
        target: 80
        published: "8080"
        protocol: tcp
networks:
  default:
    name: s04_default
volumes:
  datos_banco:
    name: s04_datos_banco

PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s04> docker network ls
NETWORK ID     NAME          DRIVER    SCOPE
ed673c3851e5   bridge        bridge    local
710f2b166cf9   host          host      local
0964a4a239c4   none          null      local
0b5e53b8fb8c   redlab        bridge    local
3942c46ccc00   s04_default   bridge    local
```