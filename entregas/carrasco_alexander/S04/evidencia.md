\# Evidencia S04 — Docker Day 2



\*\*Alumno:\*\* Diego Alexander Carrasco Quiñones



En esta práctica se trabajó con persistencia de datos, redes Docker, variables de configuración y Docker Compose. El objetivo fue comprobar mediante comandos que los contenedores pueden comunicarse entre sí, que los datos pueden sobrevivir a la eliminación de un contenedor mediante volúmenes y que la configuración puede separarse de las imágenes.

\---



\## Misión 1 — Persistencia con volúmenes



\### 1.Prueba sin volumen 



Primero creo un contenedor de PostgreSQL sin utilizar un volumen. La finalidad es demostrar que los datos almacenados directamente dentro del contenedor se pierden cuando el contenedor es eliminado.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run -d --name reto-db-sin-volumen -e POSTGRES\_PASSWORD=dev postgres:17-alpine



Primero compruebo que PostgreSQL ya esté listo para recibir conexiones:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-sin-volumen pg\_isready

/var/run/postgresql:5432 - accepting connections



Ahora creo una tabla llamada cuentas y agrego una cuenta con saldo de 100. Esto me permite tener un dato concreto que después pueda comprobar si sobrevivió o no.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-sin-volumen psql -U postgres -c "CREATE TABLE cuentas (id INT PRIMARY KEY, saldo INT); INSERT INTO cuentas VALUES (1,100);"

CREATE TABLE

INSERT 0 1



Compruebo que el registro exista:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-sin-volumen psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)



Ahora elimino completamente el contenedor. Como no utilicé ningún volumen, los datos estaban almacenados dentro del propio contenedor.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker rm -f reto-db-sin-volumen

reto-db-sin-volumen



Vuelvo a crear otro contenedor con el mismo nombre y la misma imagen, pero sigue sin tener un volumen.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run -d --name reto-db-sin-volumen -e POSTGRES\_PASSWORD=dev postgres:17-alpine



Compruebo nuevamente que PostgreSQL esté funcionando:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-sin-volumen pg\_isready

/var/run/postgresql:5432 - accepting connections



Ahora intento consultar la tabla cuentas. El error demuestra que la tabla ya no existe, por lo que los datos se perdieron al eliminar el contenedor anterior.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-sin-volumen psql -U postgres -c "SELECT \* FROM cuentas;"

ERROR:  relation "cuentas" does not exist

LINE 1: SELECT \* FROM cuentas;

&#x20;                     ^



Elimino nuevamente el contenedor para realizar la segunda parte de la prueba.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker rm -f reto-db-sin-volumen

reto-db-sin-volumen

Resultado



Sin un volumen, la información almacenada dentro del contenedor no sobrevive a la eliminación del contenedor.



2\. Prueba con volumen



Ahora realizo la misma prueba, pero utilizando un volumen nombrado. La finalidad es demostrar que los datos pueden mantenerse separados del ciclo de vida del contenedor.



Primero creo el volumen:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker volume create reto-datos

reto-datos



Ahora creo un contenedor de PostgreSQL y monto el volumen reto-datos en la ubicación donde PostgreSQL almacena sus datos.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run -d --name reto-db-con-volumen -e POSTGRES\_PASSWORD=dev -v reto-datos:/var/lib/postgresql/data postgres:17-alpine



Compruebo que PostgreSQL esté disponible:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-con-volumen pg\_isready

/var/run/postgresql:5432 - accepting connections



Creo nuevamente la tabla y el registro:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-con-volumen psql -U postgres -c "CREATE TABLE cuentas (id INT PRIMARY KEY, saldo INT); INSERT INTO cuentas VALUES (1,100);"

CREATE TABLE

INSERT 0 1



Compruebo que el registro exista:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-con-volumen psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)



Ahora elimino solamente el contenedor. El volumen reto-datos permanece.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker rm -f reto-db-con-volumen

reto-db-con-volumen



Vuelvo a crear el contenedor utilizando exactamente el mismo volumen:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run -d --name reto-db-con-volumen -e POSTGRES\_PASSWORD=dev -v reto-datos:/var/lib/postgresql/data postgres:17-alpine



Compruebo que PostgreSQL esté listo:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-con-volumen pg\_isready

/var/run/postgresql:5432 - accepting connections



Finalmente vuelvo a consultar la tabla. El registro continúa existiendo, demostrando que el volumen conservó los datos aunque el contenedor anterior fue eliminado.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-db-con-volumen psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)

Resultado



Con un volumen nombrado, los datos de PostgreSQL sobreviven a la eliminación del contenedor, porque los datos están almacenados en el volumen y no dependen de la existencia del contenedor.



\---



\## Misión 2 — Comunicación entre contenedores



En esta misión compruebo que los contenedores conectados a la misma red de Docker pueden localizarse mediante el nombre del servicio o contenedor.



Primero pruebo desde otro contenedor que está conectado a la red s04\_default. Utilizo db como nombre del host.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run --rm --network s04\_default postgres:17-alpine pg\_isready -h db

db:5432 - accepting connections



El resultado demuestra que el contenedor puede resolver el nombre db y comunicarse con PostgreSQL mediante el puerto interno 5432.



Ahora hago la misma prueba, pero desde un contenedor que no está conectado a s04\_default.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run --rm postgres:17-alpine pg\_isready -h db

db:5432 - no response

Resultado



Cuando los contenedores comparten una red Docker, pueden comunicarse utilizando nombres como db. Fuera de esa red, el nombre db no puede resolverse de la misma manera.



\---



\## Misión 3 — Configuración mediante archivos `.env`



En esta misión compruebo que puedo utilizar diferentes archivos de configuración para ejecutar la misma imagen con diferentes valores.



Primero creo .env.prod, que contiene los valores correspondientes a una configuración de producción.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> @"

>> POSTGRES\_PASSWORD=produccion

>> POSTGRES\_DB=banco\_prod

>> "@ | Set-Content .env.prod



Ahora creo un contenedor para desarrollo utilizando el archivo .env.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run -d --name reto-config-dev --env-file .env postgres:17-alpine



Compruebo las variables relacionadas con PostgreSQL que recibió el contenedor:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-config-dev env | Select-String "POSTGRES"

POSTGRES\_PASSWORD=\*\*\*\*\*\*\*\*

POSTGRES\_DB=banco

PGDATA=/var/lib/postgresql/data



Después creo otro contenedor utilizando exactamente la misma imagen, pero esta vez con .env.prod.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker run -d --name reto-config-prod --env-file .env.prod postgres:17-alpine



Compruebo sus variables:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker exec reto-config-prod env | Select-String "POSTGRES"

POSTGRES\_PASSWORD=produccion

POSTGRES\_DB=banco\_prod

PGDATA=/var/lib/postgresql/data



Finalmente elimino los dos contenedores de prueba:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker rm -f reto-config-dev reto-config-prod

Resultado



La misma imagen de PostgreSQL puede utilizar diferentes configuraciones dependiendo del archivo .env que se proporcione mediante --env-file. Esto permite separar la configuración del contenido de la imagen.



Además, el archivo `.env` se encuentra incluido en `.gitignore` para evitar subir información sensible al repositorio.



\---



\## Misión 4 — Docker Compose



En esta misión utilizo compose.yaml para levantar varios servicios relacionados como un solo conjunto.



Los servicios utilizados son:



web: servidor Nginx.

db: base de datos PostgreSQL.

cache: Redis.

broker: RabbitMQ.



Además, PostgreSQL utiliza un volumen nombrado y un healthcheck.



Primero levanto todos los servicios:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose up -d

\[+] up 4/4

&#x20;✔ Container s04-cache-1   Running

&#x20;✔ Container s04-broker-1  Running

&#x20;✔ Container s04-web-1     Running

&#x20;✔ Container s04-db-1      Healthy

PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04>



Después compruebo el estado de todos los servicios:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose ps

NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS

s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    36 minutes ago   Up 36 minutes             0.0.0.0:15672->15672/tcp, \[::]:15672->15672/tcp

s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     36 minutes ago   Up 36 minutes              6379/tcp

s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        36 minutes ago   Up 36 minutes (healthy)    5432/tcp

s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       36 minutes ago   Up 36 minutes              0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp

PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04>



El resultado demuestra que los cuatro servicios están funcionando y que PostgreSQL aparece como healthy.



Comprobación de los logs de PostgreSQL



Ahora reviso los logs de db para comprobar que PostgreSQL haya iniciado correctamente y esté aceptando conexiones.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose logs -f db

db-1 | PostgreSQL init process complete; ready for start up.

db-1 | ... starting PostgreSQL 17.11 ...

db-1 | listening on IPv4 address "0.0.0.0", port 5432

db-1 | listening on IPv6 address "::", port 5432

db-1 | listening on Unix socket ...

db-1 | database system was shut down ...

db-1 | database system is ready to accept connections



La línea database system is ready to accept connections confirma que la base de datos terminó de iniciar correctamente.



Comprobación de la configuración de Compose



Utilizo docker compose config para comprobar cómo Docker interpreta el archivo compose.yaml después de procesar las variables y configuraciones.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose config

name: s04

services:

&#x20; broker:

&#x20;   image: rabbitmq:4-management-alpine

&#x20;   networks:

&#x20;     default: null

&#x20;   ports:

&#x20;     - mode: ingress

&#x20;       target: 15672

&#x20;       published: "15672"

&#x20;       protocol: tcp

&#x20; cache:

&#x20;   image: redis:8-alpine

&#x20;   networks:

&#x20;     default: null

&#x20; db:

&#x20;   environment:

&#x20;     POSTGRES\_DB: banco

&#x20;   healthcheck:

&#x20;     test:

&#x20;       - CMD-SHELL

&#x20;       - pg\_isready -U postgres

&#x20;     timeout: 3s

&#x20;     interval: 5s

&#x20;     retries: 5

&#x20;   image: postgres:17-alpine

&#x20;   networks:

&#x20;     default: null

&#x20;   volumes:

&#x20;     - type: volume

&#x20;       source: datos\_banco

&#x20;       target: /var/lib/postgresql/data

&#x20;       volume: {}

&#x20; web:

&#x20;   depends\_on:

&#x20;     db:

&#x20;       condition: service\_healthy

&#x20;       required: true

&#x20;   image: nginx:alpine

&#x20;   networks:

&#x20;     default: null

&#x20;   ports:

&#x20;     - mode: ingress

&#x20;       target: 80

&#x20;       published: "8080"

&#x20;       protocol: tcp

networks:

&#x20; default:

&#x20;   name: s04\_default

volumes:

&#x20; datos\_banco:

&#x20;   name: s04\_datos\_banco



Aquí se puede comprobar que Compose creó una red llamada s04\_default, que PostgreSQL utiliza el volumen s04\_datos\_banco, que web depende de que db esté saludable y que los puertos de Nginx y RabbitMQ están publicados.



Comprobación del contenido de Nginx



Entro al contenedor web para comprobar que Nginx tenga sus archivos HTML.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose exec web sh



Dentro del contenedor ejecuto:



/ # ls /usr/share/nginx/html

50x.html    index.html

/ #



Esto demuestra que el servidor Nginx contiene sus archivos HTML dentro del contenedor.



Comprobación de la red creada por Compose



Ahora muestro las redes existentes en Docker:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker network ls

NETWORK ID     NAME          DRIVER    SCOPE

1d3d72a38a51   bridge        bridge    local

6564016eea20   host          host      local

4cb295b71f11   none          null      local

0e607d87c732   redlab        bridge    local

80d9454bb4ab   s04\_default   bridge    local



La red s04\_default fue creada automáticamente por Docker Compose para permitir la comunicación entre los servicios del proyecto.



Después inspecciono la red:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker network inspect s04\_default



La red utiliza:



Subnet: 172.19.0.0/16

Gateway: 172.19.0.1



Los servicios recibieron direcciones dentro de esa red:



s04-cache-1  -> 172.19.0.2/16

s04-db-1     -> 172.19.0.3/16

s04-broker-1 -> 172.19.0.4/16

s04-web-1    -> 172.19.0.5/16



Esto demuestra que los cuatro servicios están conectados a la misma red interna de Docker.



Ciclo de vida de Docker Compose



Primero detengo los servicios utilizando stop. Esto detiene los contenedores, pero no los elimina.



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose stop



Después los vuelvo a iniciar:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose start



Compruebo nuevamente el estado:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose ps



Después utilizo down:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose down



down elimina los contenedores y la red creada por Compose, pero conserva el volumen nombrado.



Compruebo los volúmenes:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker volume ls



Posteriormente vuelvo a levantar los servicios:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose up -d



Compruebo que la base de datos banco continúe disponible:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose exec db psql -U postgres -c "\\l"



Finalmente pruebo down -v:



PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\S04> docker compose down -v



A diferencia de down, la opción -v también elimina los volúmenes administrados por Compose. Por lo tanto, la información almacenada en esos volúmenes también se elimina.



\---



\# Preguntas conceptuales



\## 1. ¿Qué se pierde con `docker compose down` y qué con `docker compose down -v`?



`docker compose down` elimina los contenedores y la red creada por Docker Compose, pero conserva los volúmenes nombrados.



Por otro lado, `docker compose down -v` también elimina los volúmenes nombrados asociados al proyecto.



Por lo tanto:



\* `docker compose down` → elimina contenedores y redes, pero conserva los datos del volumen.

\* `docker compose down -v` → elimina contenedores, redes y volúmenes, por lo que también se pierden los datos almacenados en esos volúmenes.



Esto se comprobó anteriormente con el volumen utilizado por PostgreSQL.



\---



\## 2. ¿Por qué `web` puede alcanzar a `db` sin publicar el puerto 5432?



Porque los servicios de Docker Compose se conectan automáticamente a una red interna del proyecto.



Dentro de esa red, los contenedores pueden comunicarse directamente utilizando el nombre del servicio como nombre DNS.



Por ejemplo, `web` puede utilizar:



```text

db:5432

```



para comunicarse con PostgreSQL.



El puerto `5432` no necesita publicarse al equipo anfitrión porque la comunicación ocurre dentro de la red interna de Docker.



Publicar:



```text

5432:5432

```



sería necesario solamente si se quisiera acceder a PostgreSQL desde fuera de esa red, por ejemplo desde el sistema anfitrión.



\---



\## 3. ¿Qué pasaría si `.env` estuviera dentro de la imagen?



Si `.env` se incluyera dentro de la imagen, los valores de configuración quedarían incorporados a la imagen y podrían terminar expuestos a cualquier persona que tuviera acceso a ella.



Esto sería especialmente peligroso si el archivo contiene contraseñas, claves o cualquier otro dato sensible.



Además, se perdería una de las ventajas de separar la configuración de la aplicación, ya que sería necesario reconstruir la imagen para cambiar esos valores.



Es mejor mantener la configuración fuera de la imagen y proporcionarla en tiempo de ejecución mediante mecanismos como `env\_file` o variables de entorno.



\---



\# Conclusión general



En esta práctica se trabajó con diferentes características de Docker y Docker Compose, lo que permitió comprender mejor cómo se pueden administrar contenedores, datos, redes y servicios dentro de una aplicación.



Primero se comprobó la diferencia entre almacenar información directamente dentro de un contenedor y utilizar volúmenes. Al eliminar un contenedor sin volumen, los datos almacenados en él se perdieron, mientras que al utilizar un volumen nombrado, la información permaneció disponible incluso después de eliminar y volver a crear el contenedor. Esto permitió comprender la importancia de los volúmenes cuando se necesita conservar información, especialmente en servicios como las bases de datos.



También se trabajó con redes de Docker y se comprobó que los contenedores conectados a una misma red pueden comunicarse entre ellos utilizando el nombre del servicio, sin necesidad de conocer su dirección IP. Además, se comprobó que un contenedor que no pertenece a esa red no puede acceder de la misma manera a los servicios internos. Esto permitió entender la diferencia entre la comunicación interna entre contenedores y la publicación de puertos hacia el equipo anfitrión.



Otro aspecto importante fue el uso de archivos .env para proporcionar diferentes configuraciones a una misma imagen. Se comprobó que es posible utilizar diferentes archivos de variables de entorno para ejecutar la misma imagen con configuraciones distintas, evitando tener que modificar la imagen para cada entorno. También se observó la importancia de no incluir archivos con contraseñas o información sensible dentro del repositorio.



Finalmente, mediante Docker Compose se integraron varios servicios en un mismo proyecto: Nginx, PostgreSQL, Redis y RabbitMQ. Se comprobó cómo Compose crea automáticamente una red para los servicios, administra los contenedores y volúmenes, permite definir dependencias mediante depends\_on y comprobar el estado de PostgreSQL mediante un healthcheck. También se revisó el ciclo de vida de los servicios utilizando stop, start, down y down -v, observando que down puede eliminar los contenedores sin eliminar los volúmenes, mientras que down -v también elimina los datos almacenados en ellos.



