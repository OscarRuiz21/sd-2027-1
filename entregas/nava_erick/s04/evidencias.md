Actividad 1. Primeramente sin volumen 

```bash
eri@saiko:~/.../s04$ docker run -d --name sin_volumen -e POSTGRES_PASSWORD=secreto postgres:17-alpine
59baec408ccfece24110b1f90df0ee5f0bbb281c3955e803f848e012a9dbc6e7
eri@saiko:~/.../s04$ docker exec -it sin_volumen psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE
eri@saiko:~/.../s04$ docker exec -it sin_volumen psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1
eri@saiko:~/.../s04$ docker exec -it sin_volumen psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo 
----+-------
  1 |   100
(1 row)

# Ahora con volumen 
eri@saiko:~/.../s04$ docker run -d --name con_volumen -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
a5955a85063a067186b8cb420be8ea9729eaf1c4b5d327f7cad9186d69131d9e
eri@saiko:~/.../s04$ docker exec -it con_volumen psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE
eri@saiko:~/.../s04$ docker exec -it con_volumen psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1
eri@saiko:~/.../s04$ docker rm -f con_volumen 
con_volumen
eri@saiko:~/.../s04$ docker run -d --name con_volumen -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
016ebd974f0abce6a021903b2ecd5287defe8de522b94431deeb8fd389fbb192
eri@saiko:~/.../s04$ docker exec -it con_volumen psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo 
----+-------
  1 |   100
(1 row)
```

# Para la actividad 2:

```bash
eri@saiko:~/.../s04$ docker compose up  -d
[+] up 4/4
 ✔ Container s04-cache-1  Running                                                                                                                                   0.0s
 ✔ Container s04-broker-1 Running                                                                                                                                   0.0s
 ✔ Container s04-web-1    Running                                                                                                                                   0.0s
 ✔ Container s04-db-1     Healthy                                                                                                                                   0.5s
eri@saiko:~/.../s04$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
eri@saiko:~/.../s04$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
eri@saiko:~/.../s04$ 
```

# Para la actividad 3:

```bash
eri@saiko:~/.../s04$ echo "POSTGRES_PASSWORD=secreto" > .env
eri@saiko:~/.../s04$ echo "POSTGRES_DB=banco" >> .env
eri@saiko:~/.../s04$ echo "POSTGRES_PASSWORD=secreto_prod" > .env.prod
eri@saiko:~/.../s04$ echo "POSTGRES_DB=banco_prod" >> .env.prod

eri@saiko:~/.../s04$ docker run -d --name dev --env-file .env postgres:17-alpine
fe0a6fcb0f062ac67844bcdeb87bfd403af08300a85527e8e41e8bd25b2b0f8b
eri@saiko:~/.../s04$ docker run -d --name prod --env-file .env.prod postgres:17-alpine
e1202394f289ebe98fc5e5e71e88b49ce01c760234a8bd9368273b86f6f93950

eri@saiko:~/.../s04$ docker exec dev env | grep POSTGRES_
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco

eri@saiko:~/.../s04$ docker exec prod env | grep POSTGRES_
POSTGRES_PASSWORD=secreto_prod
POSTGRES_DB=banco_prod
eri@saiko:~/.../s04$ 
```

# Para la actividad 4:

```bash
eri@saiko:~/.../s04$ docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED       STATUS                 PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    2 hours ago   Up 2 hours             4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     2 hours ago   Up 2 hours             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        2 hours ago   Up 2 hours (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       2 hours ago   Up 2 hours             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

## Preguntas

**¿Qué se pierde con down y qué con down -v?** 
Con down se pierden los contenedores y sus redes internas. La diferencia es que con -v también se pierden los volúmenes guardados.

**¿Por qué web alcanza a db sin publicar el puerto 5432?** 
Porque el compose crea una red interna para los contenedores.

**¿Qué pasaría si el .env estuviera dentro de la imagen?**
Se perdería el principio de portabilidad en el que la configuración debe de estar fuera del área de desarrollo. Además, se crearía un fuerte hueco de seguridad al otorgar las contraseñas y accesos reales.