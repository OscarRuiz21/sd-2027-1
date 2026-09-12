# Evidencia de Laboratorio S02 Docker

**Alumno: Echeverria Goicochea Erick Isaac**

## 1. Persistencia

* Sin volumen (los datos se pierden):

```shell
┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
docker rm -f db1
docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine


┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
docker rm -f db1
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
db1
```
* Con volumen nombrado (los datos sobreviven):

```shell

┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
docker rm -f db1
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine

┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
docker rm -f db1
 id | saldo 
----+-------
  1 |   100
(1 row)

db1

```

## 2.Red

* Dentro de la red 

```shell
┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
```
* Fuera de la red

```shell
┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
```

## 3. Configuracion
* Imagen levantada dos veces

```shell
┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker run -d --name c1 --env-file .env alpine:3.20 sleep 60
docker run -d --name c2 --env-file .env.prod alpine:3.20 sleep 60
docker exec c1 env
docker exec c2 env
docker rm -f c1 c2
bf379f35daf6376f06de09b73ca55bfdd05c34f7618c183939392e5e781f78f7
27f7a3a4158a91fdd216b396cf8acbe82e9da2ab18a6e5a7ace4f7d804497f12
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=bf379f35daf6
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
HOME=/root
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
HOSTNAME=27f7a3a4158a
POSTGRES_PASSWORD=secretoProd
POSTGRES_DB=bancoP
HOME=/root
c1
c2
```

## 4. Compose

* `compose.yaml`
```shell
┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s04]
└─$ docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"NAME           SERVICE   STATUS                    PORTS
s04-broker-1   broker    Up 16 minutes             4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 16 minutes             6379/tcp
s04-db-1       db        Up 16 minutes (healthy)   5432/tcp
s04-web-1      web       Up 15 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```
* ¿Qué se pierde con down y qué con down -v?
Usando `down` se borran los contenedores y la red, pero se conversa los volumenes, mientras que con `down -v` se borra tambien los volumenes nombrados, eliminando los datos guardados.

* ¿Por qué web alcanza a db sin publicar el puerto 5432?
Porque al estar en la misma red de Docker, los contenedores se comunican internamente entre si por el puerto del contenedor(5432). La directiva ports solo es necesaria cuando se desea exponer un puerto hacia la maquina host.

* ¿Qué pasaría si el .env estuviera dentro de la imagen?
La imagen dejaria de ser portable entre entornos y se filtrarian contrasenas o datos confidenciable  dentro del contenedor.

## Preguntas

* ¿Qué hice?
Realicé la práctica S04 configurando un sistema distribuido multicontenedor con Docker Compose y valide la persistencia de datos mediante volumenes nombrados, el descubrimiento de servicios por nombre dentro de una red virtual, y la separacion de configuracion mediante variables de entorno (.env).

* ¿Qué característica gané y cuál pagué?
Gané: Persistencia de estado independiente del ciclo de vida de los contenedores, aislamiento de red entre servicios y la capacidad de declarar e inicializar un sistema distribuido usando docker compose.

Pagué: Mayor complejidad en la gestión del almacenamiento.

* ¿Qué haría distinto si el volumen fuera 10x? (refiriéndose al tamaño de los datos/almacenamiento)
En lugar de usar volúmenes locales administrados en una sola máquina, usaria un sistema de almacenamiento distribuido ademas de hacer uso respaldos.
