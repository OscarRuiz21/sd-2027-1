# S04: Estado, red, configuración y compose

## Misión 1: Persistencia

### Sin volumen: crear tabla, insertar y consultar
```
docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)
```

### Sin volumen: borrar contenedor y recrearlo
```
docker rm -f db1
db1

docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
2b68eea1b715846ae045a5120a4bf75078ff1644b61dc4a5390767365d77f7a6

docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

### Con volumen: crear tabla, insertar, borrar contenedor y recrearlo
```
docker rm -f db1
db1

docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
71b9cc934564fa59980902143f82e78fc320f23a6d915451be02ee76fb0bf1ab

docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

docker rm -f db1
db1

docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
5cdec7813955433922983178beaa855b51f49d0efb80c1fdbb0d4519b9387655

docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)
```

## Misión 2: Red

### Compose arriba y pg_isready dentro de la red
```
docker compose up -d
[+] up 6/6
 ✔ Network s04_default    Created
 ✔ Volume s04_datos_banco Created
 ✔ Container s04-db-1     Healthy
 ✔ Container s04-cache-1  Started
 ✔ Container s04-broker-1 Started
 ✔ Container s04-web-1    Started

docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
```

### pg_isready fuera de la red
```
docker run --rm s04_default postgres:17-alpine pg_isready -h db
Unable to find image 's04_default:latest' locally
docker: Error response from daemon: pull access denied for s04_default, repository does not exist or may require 'docker login'
```

## Misión 3: Configuración

### Levantar db_dev y db_prod con distinto .env
```
docker run -d --name db_dev  --env-file .env      -e POSTGRES_PASSWORD=dev  -e POSTGRES_DB=banco_dev  postgres:17-alpine
e9acafdf55773636d1e4b6535c5fc19a450892205ae2d42d4b066195b7b3a6fb

docker run -d --name db_prod --env-file .env.prod -e POSTGRES_PASSWORD=prod -e POSTGRES_DB=banco_prod postgres:17-alpine
836b5da217e075410c83985c45e04a1b76029532716f359de1440a698594070a
```

### Variables de entorno de db_dev
```
docker exec db_dev env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=e9acafdf5577
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
```

### Variables de entorno de db_prod
```
docker exec db_prod env
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=836b5da217e0
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
GOSU_VERSION=1.19
LANG=en_US.utf8
PG_MAJOR=17
PG_VERSION=17.11
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979
DOCKER_PG_LLVM_DEPS=llvm21-dev          clang21
PGDATA=/var/lib/postgresql/data
HOME=/root
```

## Misión 4: Compose

### Levantar compose
```
docker compose up -d
[+] up 4/4
 ✔ Container s04-web-1    Running
 ✔ Container s04-cache-1  Running
 ✔ Container s04-broker-1 Running
 ✔ Container s04-db-1     Healthy
```

### docker compose ps
```
docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED             STATUS                    PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    About an hour ago   Up About an hour          0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     About an hour ago   Up About an hour          6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        12 seconds ago      Up 12 seconds (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       About an hour ago   Up About an hour          0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

### ¿Qué se pierde con down y qué con down -v?

Con el down sin -v, se borra los contenedores y la red del proyecto, pero el volumen, es decir, los datos, sobreviven. 

En cambio, con -v, se borran los tres: tanto contenedores, volúmenes y la red

### ¿Por qué web alcanza a db sin publicar el puerto 5432?

Lo que hace compose es crear una red por proyecto, y dicha red conecta todos los servicios. Los puertos se publican para que se pueda entrar fuera del Docker, pero en sí los contenedores se pueden comunicar entre sí dentro de esta red, des decir, no lo busca como localhost:5432, sino como db:5432, es decir, usa el nombre de servicio.

### ¿Qué pasaría si el .env estuviera dentro de la imagen?

La configuración quedaría dentro de la imagen, y para cambiar una contraseña o nombre se tendría que reconstruir la imagen, lo que hará que no se cumpla el punto 3 de la Lectura 3 vista anteriormente; a su vez, esta misma imagen no serviría para dos entornos si, pero lo más riesgoso sería que la contraseña se quedaría en el registro de imágenes, por lo que cualquiera que tuviese acceso a la imagen tendría las credenciales.