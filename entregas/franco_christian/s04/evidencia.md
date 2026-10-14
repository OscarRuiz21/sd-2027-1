>[!Universidad Nacional Autónoma de México]
>Nombre: Christian Franco Ramírez
>Materia: Sistemas Distribuidos
>Semestre: 2027-1
>Grupo: 02
>Profesor: Oscar Manuel Ruiz Hurtado

---
# Misión 1
**Persistencia.** Demuestra, con las salidas pegadas como texto, que una fila **sobrevive** a `docker rm -f` cuando hay volumen y **no sobrevive** cuando no lo hay. Las dos corridas, el `ERROR` incluido.
### Caso sin volumen

```bash
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run -d --name  
db_sin_volumen -e POSTGRES_PASSWORD=secreto postgres:17-alpine  
7828dac38964f15645ed2a6b58ebcb9f73407b6308df315b62c6a20ceb833ffa  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec -it db_si  
n_volumen psql -U postgres -c "CREATE TABLE cuentas(id INT PRIMARY KEY, saldo INT);"  
CREATE TABLE  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec -it db_si  
n_volumen psql -U postgres -c "INSERT INTO cuentas VALUES (1,100);"  
INSERT 0 1  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec -it db_si  
n_volumen psql -U postgres -c "SELECT * FROM cuentas;"  
id | saldo    
----+-------  
 1 |   100  
(1 row)  
  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker rm -f db_sin_v  
olumen  
db_sin_volumen  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run -d --name  
db_sin_volumen -e POSTGRES_PASSWORD=secreto postgres:17-alpine  
d6b253e900fb2f98c575e35248081866ebed7ab8e42c939e6a328355c0c247e7  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec -it db_si  
n_volumen psql -U postgres -c "SELECT * FROM cuentas;"  
ERROR:  relation "cuentas" does not exist  
LINE 1: SELECT * FROM cuentas;  
                     ^  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$
```
### Caso con volumen 

```bash
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker volume create  
datos_prueba  
datos_prueba  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run -d \  
 --name db_con_volumen \  
 -e POSTGRES_PASSWORD=secreto \  
 -v datos_prueba:/var/lib/postgresql/data \  
 postgres:17-alpine  
fa9692997c551ee1b7cd2119900cfe82716a9fd399543d89980f0da6160333d4  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec -it db_co  
n_volumen psql -U postgres -c "CREATE TABLE cuentas(id INT PRIMARY KEY, saldo INT);"  
docker exec -it db_con_volumen psql -U postgres -c "INSERT INTO cuentas VALUES (1,100);"  
CREATE TABLE  
INSERT 0 1  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec -it db_co  
n_volumen psql -U postgres -c "SELECT * FROM cuentas;"  
id | saldo    
----+-------  
 1 |   100  
(1 row)  
  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker rm -f db_con_v  
olumen  
db_con_volumen  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run -d \  
 --name db_con_volumen \  
 -e POSTGRES_PASSWORD=secreto \  
 -v datos_prueba:/var/lib/postgresql/data \  
 postgres:17-alpine  
d04ab31062d42534017c803ea2e240d38b33be7be893d59a7845abd7634c3983  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec -it db_co  
n_volumen psql -U postgres -c "SELECT * FROM cuentas;"  
id | saldo    
----+-------  
 1 |   100  
(1 row)
```

---
# Misión dos

**Red.** Desde otro contenedor, alcanza a `db` **por nombre** con `pg_isready -h db` (pista: `docker run --rm --network s04_default postgres:17-alpine pg_isready -h db`, con tu compose arriba). Pega la respuesta, y pega también el error cuando **no** está en la misma red (quita el `--network`).

### Desde otro contenedor usando `s04_default`

```bash
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker compose ps  
NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    
 PORTS  
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    38 minutes ago   Up 27 minutes             
 4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp  
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     38 minutes ago   Up 27 minutes             
 6379/tcp  
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        38 minutes ago   Up 27 minutes (healthy)  
 5432/tcp  
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       38 minutes ago   Up 27 minutes             
 0.0.0.0:8080->80/tcp, [::]:8080->80/tcp  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run --rm --net  
work s04_default postgres:17-alpine pg_isready -h db  
db:5432 - accepting connections
```

### Sin estar en la misma red

```bash
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker compose ps  
NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    
 PORTS  
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    42 minutes ago   Up 31 minutes             
 4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp  
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     42 minutes ago   Up 31 minutes             
 6379/tcp  
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        42 minutes ago   Up 31 minutes (healthy)  
 5432/tcp  
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       42 minutes ago   Up 31 minutes             
 0.0.0.0:8080->80/tcp, [::]:8080->80/tcp  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run --rm postg  
res:17-alpine pg_isready -h db  
db:5432 - no response
```

---
# Misión 3.

### Creación `.env`

```bash
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ cat .env  
POSTGRES_PASSWORD=secreto_dev  
POSTGRES_DB=banco_dev

christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run -d --name  
postgres_dev --env-file .env postgres:17-alpine  
42bf656a140fcc5844ad3b7bdd275d36331f343a8f0d8ea02ed10718bb2041c9

christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec postgres_  
dev env  
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin  
HOSTNAME=42bf656a140f  
POSTGRES_PASSWORD=secreto_dev  
POSTGRES_DB=banco_dev  
GOSU_VERSION=1.19  
LANG=en_US.utf8  
PG_MAJOR=17  
PG_VERSION=17.11  
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979  
DOCKER_PG_LLVM_DEPS=llvm21-dev          clang21  
PGDATA=/var/lib/postgresql/data  
HOME=/root
```

### Creación `.env.prod`

```bash
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ cat .env.prod  
POSTGRES_PASSWORD=secreto_prod  
POSTGRES_DB=banco_prod  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker run -d --name  
postgres_prod --env-file .env.prod postgres:17-alpine  
6441f35c912978f8271ada55788535222c479fe6af430502da93ac8f4bcd2e23  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker exec postgres_  
prod env  
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin  
HOSTNAME=6441f35c9129  
POSTGRES_PASSWORD=secreto_prod  
POSTGRES_DB=banco_prod  
GOSU_VERSION=1.19  
LANG=en_US.utf8  
PG_MAJOR=17  
PG_VERSION=17.11  
PG_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979  
DOCKER_PG_LLVM_DEPS=llvm21-dev          clang21  
PGDATA=/var/lib/postgresql/data  
HOME=/root
```

### Creación de `.gitignore`

```bash
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ cat .env.example  
POSTGRES_PASSWORD=cambia-esto  
POSTGRES_DB=nombre_base_datos  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ cat .gitignore  
.env  
.env.prod  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$
```

# Misión 4

**Compose.** Tu `compose.yaml` con al menos `web`, `db` y `cache`, un volumen nombrado y `env_file`. Pega `docker compose ps` como texto. Y en `evidencia.md` responde: _¿qué se pierde con `down` y qué con `down -v`?_ · _¿por qué `web` alcanza a `db` sin publicar el puerto 5432?_ · _¿qué pasaría si el `.env` estuviera dentro de la imagen?_

```
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ cat compose.yaml    
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
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker compose up -d  
[+] up 4/4  
✔ Container s04-cache-1  Running                                                                                     0.0s  
✔ Container s04-broker-1 Running                                                                                     0.0s  
✔ Container s04-web-1    Running                                                                                     0.0s  
✔ Container s04-db-1     Healthy                                                                                     7.9s  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/franco_christian/s04$ docker compose ps  
NAME           IMAGE                          COMMAND                  SERVICE   CREATED             STATUS                 
    PORTS  
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    About an hour ago   Up 54 minutes          
    4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp  
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     About an hour ago   Up 54 minutes          
    6379/tcp  
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        47 seconds ago      Up 45 seconds (health  
y)   5432/tcp  
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       About an hour ago   Up 53 minutes          
    0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

1. **¿Qué se pierde con `docker compose down` y qué con `docker compose down -v`?**

`docker compose down` elimina los contenedores y la red creada por Compose, pero conserva los volúmenes nombrados. Por lo tanto, los datos almacenados en `datos_banco` permanecen.

`docker compose down -v` también elimina los volúmenes nombrados. En este caso, se elimina `datos_banco` y se pierden los datos almacenados por PostgreSQL.

2. **¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?**

Los servicios de Docker Compose se conectan automáticamente a la misma red interna, en este caso `s04_default`. Dentro de esa red, Docker proporciona resolución de nombres entre servicios, por lo que `web` puede comunicarse con PostgreSQL usando `db:5432`.

No es necesario publicar el puerto 5432 en el host, porque la comunicación ocurre directamente entre los contenedores mediante la red interna de Docker.

3. **¿Qué pasaría si el `.env` estuviera dentro de la imagen?**

Las variables sensibles, como contraseñas, quedarían incorporadas en la imagen. Cualquier persona con acceso a esa imagen podría potencialmente obtener esos secretos. Además, cambiar la configuración requeriría construir o modificar la imagen.

Por esta razón, el `.env` debe mantenerse fuera de la imagen y fuera del repositorio, mientras que `.env.example` puede incluirse como plantilla con valores ficticios.