# Joshua Rodriguez Zamora

## Mision 1

Persistencia: Demuestra, con las salidas pegadas como texto, que una fila sobrevive a docker rm -f cuando hay volumen y no sobrevive cuando no lo hay. Las dos corridas, el ERROR incluido.

### Comandos generales

|Comandos| Función | Salida esperada |
| :---: | :---:| :---: |
|docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine | Crea una base de datos  | What's next: ... |
|docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"|Crea una tabla de nombre db1 | db1 CREATE TABLE |
|docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"| Se insertan valores en la tabla db1 | db 1 INSERT 0 1|
|docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"|Revisión de datos con una query|  id,  saldo 1, 100 |
|$ docker rm -f db1 | Se elimina la base de datos | db1 |


### Comparativa
----

### ***Sin volumen***

```bash
```
Al borrar y volver a crear la base de datos desde cero, hacemos la misma query que anteriormente sólo para descubrir que no existen más los datos, esto con el siguiente comando: 
```bash
### Creando de nuevo la base de datos
docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine

$ docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

ERROR:  relation "cuentas" does not exist 
LINE 1: SELECT * FROM cuentas;
```
### ***Con volumen***

De igual manera, se crea una base de datos, siguiendo los pasos de los comandos generales, aunque teniendo en consideración un nuevo comando, **"-v"**, esta instrucción permite crear la base de datos y almacenar sus datos en un volumen. Especificamente una carpeta exterior donde todo lo que escribe el contenedor se queda fuera.

```bash

### Creando una base de datos en un volumen:
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine 

### Añadiendo valores a la DB
docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

### Borrandola
docker rm -f db1

### Volviendo a inicializar la DB
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine

### Query
docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"


### resultado

 id | saldo
----+-------
  1 |   100
(1 row)

Demostrando así, que aunque el contenedor dejó de existir, el volumen no
```


## Mision 2

Red. Desde otro contenedor, alcanza a db por nombre con pg_isready -h db (pista: docker run --rm --network s04_default postgres:17-alpine pg_isready -h db, con tu compose arriba). Pega la respuesta, y pega también el error cuando no está en la misma red (quita el --network).

1. Alcanzando a DB:

```bash
jack1@LAPTOP-2CDLHG4C MINGW64 ~/sd-2027-1/entregas/rodriguez_joshua/s04 (entregas_rodriguez_joshua)
$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
```

2. DB está en otra red:
```bash
jack1@LAPTOP-2CDLHG4C MINGW64 ~/sd-2027-1/entregas/rodriguez_joshua/s04 (entregas_rodriguez_joshua)
$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
```

## Misión 3

Configuración. La misma imagen levantada dos veces con dos .env distintos (por ejemplo .env y .env.prod, con --env-file), y el docker exec … env de cada una pegado. En el repo: .env dentro de .gitignore y un .env.example con los nombres de las variables y valores falsos.

```bash
jack1@LAPTOP-2CDLHG4C MINGW64 ~/sd-2027-1/entregas/rodriguez_joshua/s04 (entregas_rodriguez_joshua)
$ docker exec db_dev env | grep POSTGRES
docker exec db_prod env | grep POSTGRES

POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
```

## Misión 4

Compose. Tu compose.yaml con al menos web, db y cache, un volumen nombrado y env_file. Pega docker compose ps como texto. Y en evidencia.md responde: 

```bash
jack1@LAPTOP-2CDLHG4C MINGW64 ~/sd-2027-1/entregas/rodriguez_joshua/s04 (entregas_rodriguez_joshua)
$ docker ps
CONTAINER ID   IMAGE                          COMMAND                  CREATED          STATUS
        PORTS                                             NAMES
181566a6ea2d   nginx:alpine                   "/docker-entrypoint.…"   27 minutes ago   Up 27 minutes          80/tcp                                            dbweb
e4ce588cf6db   postgres:17-alpine             "docker-entrypoint.s…"   41 minutes ago   Up 41 minutes          5432/tcp                                          db1
f72397693789   nginx:alpine                   "/docker-entrypoint.…"   2 hours ago      Up 2 hours             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp           s04-web-1
77ae323fb3f8   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   2 hours ago      Up 2 hours             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp   s04-broker-1
dbca5a8c6f42   postgres:17-alpine             "docker-entrypoint.s…"   2 hours ago      Up 2 hours (healthy)   5432/tcp                                          s04-db-1
687d9a71f87f   redis:8-alpine                 "docker-entrypoint.s…"   2 hours ago      Up 2 hours             6379/tcp                                          s04-cache-1
ed4bcd815fcd   postgres:17-alpine             "docker-entrypoint.s…"   2 hours ago      Up 2 hours             5432/tcp                                          db_prod
d3e53096b4c6   postgres:17-alpine             "docker-entrypoint.s…"   2 hours ago      Up 2 hours             5432/tcp                                          db_dev
29cdd97f45c1   nginx:alpine                   "/docker-entrypoint.…"   2 hours ago      Up 2 hours             80/tcp                                            web
```

**¿qué se pierde con down y qué con down -v?** 

Docker compose down elimina tanto contenedores como la red, sin embargo, mantiene intacto el volumen y los datos del mismo, mientras que docker compose down -v elimina absolutamente todo. La red, el contenedor y el volumen, siendo que elimina todo rastro de un volumen.

**¿por qué web alcanza a db sin publicar el puerto 5432?**

Porque son parte de la misma red privada y se comunican entre sí.


**¿qué pasaría si el .env estuviera dentro de la imagen?**

Se compartirían las identificaciones o credenciales personales de nuestros contenedores, por lo que si alguien revisa el repositorio, podría hacer uso de nuestras credenciales para visualizar nuestra información sensible.