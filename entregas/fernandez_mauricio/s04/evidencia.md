Evidencia - Lab S04 Docker, día dos:estado, red, configuración y compose



Alumno: Mauricio Fernandez

Directorio: entregas/fernandez\_mauricio/s04



1\. Ejercicio 4. docker compose ps

""""""

NAME           SERVICE   STATUS                    PORTS

s04-broker-1   broker    Up 51 seconds             0.0.0.0:15672->15672/tcp, \[::]:15672->15672/tcp

s04-cache-1    cache     Up 51 seconds             6379/tcp

s04-db-1       db        Up 51 seconds (healthy)   5432/tcp

s04-web-1      web       Up 45 seconds             0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp



""""""





=\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*=



RETO



=\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*\*=



=========================== Misión 1: ===========================

1.1.- Creamos una nueva Tabla y verificamos los datos que insertamos.

""""""


PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run -d --name reto\_db1 -e POSTGRES\_PASSWORD= postgres:17-alpine

95aee644c2a22c812618283840434377e50939981081c0dda0a2617bd4c90580

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"

CREATE TABLE

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

INSERT 0 1

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db1 psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)

""""""

1.2.- Eliminamos el contenedor, creamos otro e intentamos consultar la tabla. No se conservarán los datos insertados.

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker rm -f reto\_db1reto\_db1

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run -d --name reto\_db1 -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

4c74065eefc97dd628739bce58319f08008bda542dc7f3f177001ae88d12ac43

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db1 psql -U postgres -c "SELECT \* FROM cuentas;"

ERROR:  relation "cuentas" does not exist

LINE 1: SELECT \* FROM cuentas;

""""""


1.3.- Definimos un volumen, creamos un contenedor, una tabla, y le insertamos datos.

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker volume create reto\_pgdata

reto\_pgdata

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run -d --name reto\_db2 -e POSTGRES\_PASSWORD=secreto -v reto\_pgdata:/var/lib/postgresql/data postgres:17-alpine

053b66531ca44ad3070b674e79f78d8f8d0c4acd826fa957fabdc3b43bbff8ad

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db2 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"

CREATE TABLE

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db2 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

INSERT 0 1

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db2 psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)

""""""

1.4.- Borramos el contenedor creado en el paso anterior, definimos otro apuntando en la misma dirección, y verificamos que ahora si se hayan conservado los datos.

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker rm -f reto\_db2

reto\_db2

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run -d --name reto\_db3 -e POSTGRES\_PASSWORD=secreto -v reto\_pgdata:/var/lib/postgresql/data postgres:17-alpine

657a502b496733fe032f3ef5f63d4088bf8c362687a2b176d3b30b97b1b8095b

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec reto\_db3 psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)


""""""

Conclusión: Los datos insetados en la tabla se conservaron. El contenedor es reemplazable, pero los datos persisten porque están almacenados en el volumen reto\_pgdata.

=================================================================================

=========================== Misión 2 ===========================

2.1.- Levantamos compose y verificamos que este disponible:

""""""



PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker compose up -d

\[+] up 4/4

&#x20;✔ Container s04-db-1     Healthy                                                                                   0.6s

&#x20;✔ Container s04-cache-1  Running                                                                                   0.0s

&#x20;✔ Container s04-broker-1 Running                                                                                   0.0s

&#x20;✔ Container s04-web-1    Running                                                                                   0.0s

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04>







&#x20;                                                                                              > docker compose ps

NAME           IMAGE                          COMMAND                  SERVICE   CREATED       STATUS                 PORTS

s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    6 hours ago   Up 5 hours             0.0.0.0:15672->15672/tcp, \[::]:15672->15672/tcp

s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     6 hours ago   Up 5 hours             6379/tcp

s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        6 hours ago   Up 5 hours (healthy)   5432/tcp

s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       6 hours ago   Up 5 hours             0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp

""""""

2.2.- Ejecutamos db dentro de la misma red:

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run --rm --network s04\_default postgres:17-alpine pg\_isready -h db

db:5432 - accepting connections

""""""

Se encuentra al servicio sin problemas haciendo uso del DNS de la red.

2.3.- Ejecutamos la misma prueba sin --network

""""""


PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run --rm postgres:17-alpine pg\_isready -h db

db:5432 - no response



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

"""""

Docker no encuentra DB a sin hacer uso de la red anterior.

=================================================================================


=========================== Misión 3 ===========================

3.1.- Usaremos el .env ya creado durante la práctica como la primera imagen y crearemos otra (.env.prod) para comparar su contenido.
Creando y definiendo ambos contenedores:

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> @"

>> POSTGRES\_PASSWORD=cambia\_prod

>> POSTGRES\_DB=banco\_prod

>> "@ | Set-Content .env.prod

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run -d --name config\_dev --env-file .env postgres:17-alpine

864c27375e5ab2fdcef4dc4b9890e221dda13a9733f6640236b3006106f39b85

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker run -d --name config\_prod --env-file .env.prod postgres:17-alpine

b78e9020e7bec3ac02728515eb48a9def47dfe9237705f20ed6dda98ab3cea36

""""""

3.2.- Usando docker exec visualizamos las salidas de cada contenedor:

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec config\_dev env

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

HOSTNAME=864c27375e5a

POSTGRES\_PASSWORD=cambia

POSTGRES\_DB=banco

GOSU\_VERSION=1.19

LANG=en\_US.utf8

PG\_MAJOR=17

PG\_VERSION=17.11

PG\_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979

DOCKER\_PG\_LLVM\_DEPS=llvm21-dev          clang21

PGDATA=/var/lib/postgresql/data

HOME=/root

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker exec config\_prod env

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

HOSTNAME=b78e9020e7be

POSTGRES\_PASSWORD=cambia\_prod

POSTGRES\_DB=banco\_prod

GOSU\_VERSION=1.19

LANG=en\_US.utf8

PG\_MAJOR=17

PG\_VERSION=17.11

PG\_SHA256=dd27f2b3c59e73ed14aa3324901242bf69a032a6347805f274e6260322d42979

DOCKER\_PG\_LLVM\_DEPS=llvm21-dev          clang21

PGDATA=/var/lib/postgresql/data

HOME=/root



=================================================================================



=========================== Misión 4 ===========================



¿Qué se pierde con down y qué con down -v?



"docker compose down" elimina los contenedores y la red creada por "Compose", pero conserva los volúmenes nombrados. Por eso, los datos almacenados en "datos\_banco" permanecen. En cambio, "docker compose down -v" también elimina los volúmenes, por lo que se pierde la información persistida en ellos.



¿Por qué web alcanza a db sin publicar el puerto 5432?



Porque los servicios de un mismo "compose.yaml" se conectan automáticamente a una red interna de Docker. Dentro de esa red, web puede encontrar a db mediante su nombre de servicio y conectarse directamente al puerto 5432. Publicar 5432 sería necesario para acceder a PostgreSQL desde fuera de esa red, pero no para la comunicación entre los servicios de Compose.



¿Qué pasaría si el .env estuviera dentro de la imagen?



Las credenciales o contraseñas y demás valores de configuración quedarían incorporados en la imagen y podrían ser expuestos a cualquiera que tuviera acceso a ella. Además, cambiar la configuración requeriría construir otra imagen. Es mejor mantener la configuración fuera de la imagen y proporcionarla mediante variables de entorno o archivos como .env, manteniendo los secretos fuera del repositorio.

Resultado de "Docker Compose PS":

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s04> docker compose ps

NAME           IMAGE                          COMMAND                  SERVICE   CREATED        STATUS                  PORTS

s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    11 hours ago   Up 11 hours             0.0.0.0:15672->15672/tcp, \[::]:15672->15672/tcp

s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     11 hours ago   Up 11 hours             6379/tcp

s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        11 hours ago   Up 11 hours (healthy)   5432/tcp

s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       11 hours ago   Up 11 hours             0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp

""""""



