# Evidencia S04 - Sanchez Mayen Tristan Qesen

## MISION 1: Borrar contenedor con volumen y sin volumen

1. Sin volumen
```bash
qesensinu@MacBook-Air-de-Tristan s04 % docker rm -f db1
docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
db1
7f38e7c709c062963bd60cb09b65dfee8835052cf955e64a20e978b367c16c39
qesensinu@MacBook-Air-de-Tristan s04 % docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

2. Con volumen
```bash
qesensinu@MacBook-Air-de-Tristan s04 % docker rm -f db1
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
db1
f42cb08bf810c120f516f17795d5dedd47b29cccbf621c7a2c370ec8c457f9bc
 id | saldo 
----+-------
  1 |   100
(1 row)
```

## MISION 2: Alcanzar la db cuando se esta en la misma red y cuando no

1. Cuando se tiene la db en la misma red
```bash
qesensinu@MacBook-Air-de-Tristan s04 % docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
```

2. Cuando no se tiene la db en la misma red
```bash
qesensinu@MacBook-Air-de-Tristan s04 % docker run --rm postgres:17-alpine pg_isready -h db 
db:5432 - no response

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
```

## MISION 3: Levantar la misma imagen dos veces con .env distintos

1. docker exec de cada imagen
```bash
qesensinu@MacBook-Air-de-Tristan s04 % docker exec db_dev env | grep POSTGRES
docker exec db_prod env | grep POSTGRES
POSTGRES_DB=banco_dev
POSTGRES_PASSWORD=dev
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
```


## MISION 4: Compose.yaml
```bash
qesensinu@MacBook-Air-de-Tristan s04 % docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    25 minutes ago   Up 25 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     25 minutes ago   Up 25 minutes             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        25 minutes ago   Up 25 minutes (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       25 minutes ago   Up 25 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

- ¿Qué se pierde con down y qué con down -v?: 
Docker compose down detiene y elimina los contenedores y la red interna, pero conserva los volúmenes nombrados, es decir, que los datos que se tengan se van a mantener. 
Docker compose down -v elimina los contenedores, la red y también los volúmenes nombrados, borrando permanentemente todos los datos guardados en ellos.

- ¿Por qué web alcanza a db sin publicar el puerto 5432?: 
Porque Docker Compose crea una red privada para los servicios. Los contenedores dentro de esa misma red pueden comunicarse directamente a través de sus puertos internos.

- ¿Qué pasaría si el .env estuviera dentro de la imagen?: 
Las contraseñas, credenciales o configuracion sensible quedarían dentro de las imagenes de Docker y al subirlas a un repositorio cualquier persona podría acceder y exponer nuestra informacion.