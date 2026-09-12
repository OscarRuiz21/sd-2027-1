\# Evidencia · Sesion 04



\## 1. Comandos ejecutados en el reto

```bash

\# Mision 1: Persistencia sin volumen

docker run -d --name db1 -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

sleep 5

docker exec db1 psql -U postgres -c "CREATE TABLE cuentas (id int primary key, saldo int);"

docker exec db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

docker exec db1 psql -U postgres -c "SELECT \* FROM cuentas;"

docker rm -f db1

docker run -d --name db1 -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

sleep 5

docker exec db1 psql -U postgres -c "SELECT \* FROM cuentas;"

docker rm -f db1



\# Mision 1: Persistencia con volumen nombrado

docker run -d --name db1 -v datos\_banco:/var/lib/postgresql/data -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

sleep 5

docker exec db1 psql -U postgres -c "CREATE TABLE cuentas (id int primary key, saldo int);"

docker exec db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

docker rm -f db1

docker run -d --name db1 -v datos\_banco:/var/lib/postgresql/data -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

sleep 5

docker exec db1 psql -U postgres -c "SELECT \* FROM cuentas;"

docker rm -f db1



\# Mision 2: Red y resolucion de nombres

docker run --rm --network s04\_default postgres:17-alpine pg\_isready -h db

docker run --rm postgres:17-alpine pg\_isready -h db



\# Mision 3: Variables de entorno (Factor III)

docker run -d --name db\_dev --network redlab --env-file .env.dev postgres:17-alpine

docker run -d --name db\_prod --network redlab --env-file .env.prod postgres:17-alpine

docker exec db\_dev env | grep POSTGRES

docker exec db\_prod env | grep POSTGRES

docker rm -f db\_dev db\_prod



\# Mision 4: Arranque con Docker Compose

docker compose up -d

docker compose ps



APARTADO DE TABLA:



NAME           IMAGE                        COMMAND                  SERVICE   CREATED          STATUS                    PORTS

s04-broker-1   rabbitmq:4-management-alpine "docker-entrypoint.s…"   broker    17 seconds ago   Up 15 seconds             0.0.0.0:15672->15672/tcp, \[::]:15672->15672/tcp

s04-cache-1    redis:8-alpine               "docker-entrypoint.s…"   cache     17 seconds ago   Up 16 seconds             6379/tcp

s04-db-1       postgres:17-alpine           "docker-entrypoint.s…"   db        17 seconds ago   Up 16 seconds (healthy)   5432/tcp

s04-web-1      nginx:alpine                 "/docker-entrypoint.…"   web       16 seconds ago   Up 10 seconds             0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp



\# APARTADO DE ERROR Y SALIDAS:



\# Error por falta de volumen (Mision 1):

ERROR:  relation "cuentas" does not exist

LINE 1: SELECT \* FROM cuentas;

&#x20;              ^



\# Salida con volumen (Mision 1):

&#x20;id | saldo 

\----+-------

&#x20; 1 |   100

(1 row)



\# Salidas de red (Mision 2):

db:5432 - accepting connections

db:5432 - no response



\# Salidas de variables de entorno (Mision 3):

POSTGRES\_PASSWORD=dev

POSTGRES\_DB=banco\_dev

POSTGRES\_PASSWORD=prod

POSTGRES\_DB=banco\_prod



\# preguntas

\# QUE SE PIERDE CON DOWN Y QUE CON DOWN -V:

\# Con docker compose down se borran los contenedores y la red creada para el proyecto, pero los volumenes y los datos que contienen se conservan intactos. Con docker compose down -v ademas se eliminan definitivamente los volumenes asociados, borrando todos los datos guardados.



\# POR QUE WEB ALCANZA A DB SIN PUBLICAR EL PUERTO 5432:

\# Porque docker compose crea por defecto una red propia donde los servicios se comunican directamente a traves del DNS interno de Docker usando su nombre de servicio como direccion. Publicar puertos solo es necesario para exponer un servicio hacia la maquina host externa.



\# QUE PASARIA SI EL .ENV ESTUVIERA DENTRO DE LA IMAGEN:

\# Se romperia el Factor III de doce factores (configuracion en el entorno). Estarian expuestas contrasenas y credenciales sensibles dentro de la imagen, y ademas se tendria que reconstruir una nueva imagen por cada entorno (desarrollo, pruebas o produccion) en lugar de reutilizar la misma.

