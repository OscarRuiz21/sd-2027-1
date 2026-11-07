MISIÓN 1

emili@BOOK-EV4EELQ1E2 MINGW64 ~/sd-2027-1/entregas/salgado_roman/s04 (entregas_salgado_roman)
$ docker run -d --name db_tmp -e POSTGRES_PASSWORD=secreto postgres:17-alpine
sleep 5
docker exec db_tmp psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int); INSERT INTO cuentas VALUES (1, 100);"
docker rm -f db_tmp
docker run -d --name db_tmp -e POSTGRES_PASSWORD=secreto postgres:17-alpine
sleep 5
docker exec db_tmp psql -U postgres -c "SELECT * FROM cuentas;"
c82e0a4c36d0b2d369c7234f2fc99e610cbf062e175c57e8e605d5693a4a3661
CREATE TABLE
INSERT 0 1
db_tmp
be0df735fb01e19d9cb20a22dd0ca7cc8c22d09074c5957435116e83bfe3a7a1
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;




emili@BOOK-EV4EELQ1E2 MINGW64 ~/sd-2027-1/entregas/salgado_roman/s04 (entregas_salgado_roman)
$ docker run -d --name db_vol -v prueba_datos:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
sleep 5
docker exec db_vol psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int); INSERT INTO cuentas VALUES (1, 100);"
docker rm -f db_vol
docker run -d --name db_vol -v prueba_datos:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
sleep 5
docker exec db_vol psql -U postgres -c "SELECT * FROM cuentas;"
ad3de1f3fcb15e9c06c2799c2f3ba1345ee0057402bcc58915e2627376b6dd80
CREATE TABLE
INSERT 0 1
db_vol
ddad6cd30b78b3a3e976156fb39b73113143a4bcd738253db4879679c1e7ac3b
 id | saldo
----+-------
  1 |   100
(1 row)


MISIÓN 2

emili@BOOK-EV4EELQ1E2 MINGW64 ~/sd-2027-1/entregas/salgado_roman/s04 (entregas_salgado_roman)
$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections

emili@BOOK-EV4EELQ1E2 MINGW64 ~/sd-2027-1/entregas/salgado_roman/s04 (entregas_salgado_roman)
$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"


MISIÓN 3

emili@BOOK-EV4EELQ1E2 MINGW64 ~/sd-2027-1/entregas/salgado_roman/s04 (entregas_salgado_roman)
$ echo "POSTGRES_PASSWORD=produccion" > .env.prod
docker run -d --name db_dev_test --env-file .env postgres:17-alpine
docker run -d --name db_prod_test --env-file .env.prod postgres:17-alpine
de40b56860a5f7a6a2827670c247983d1c52eda5e9ab08d5ad888b5612c2e170
52e0e160756c0cfced4bdfbc4cf412b93d516be9dbf79eeac5d01705398f85c7

emili@BOOK-EV4EELQ1E2 MINGW64 ~/sd-2027-1/entregas/salgado_roman/s04 (entregas_salgado_roman)
$ docker exec db_dev_test env | grep POSTGRES
docker exec db_prod_test env | grep POSTGRES
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
POSTGRES_PASSWORD=produccion


DOCKER COMPOSE PS
emili@BOOK-EV4EELQ1E2 MINGW64 ~/sd-2027-1/entregas/salgado_roman/s04 (entregas_salgado_roman)
$ docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED        STATUS                  PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    31 hours ago   Up 31 hours             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     31 hours ago   Up 31 hours             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        31 hours ago   Up 31 hours (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       31 hours ago   Up 31 hours             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp



¿Qué se pierde con down y qué con down -v?
Con down se borran los contenedores y la red, pero el volumen sobrevive, manteniendo tus datos seguros. Con down -v se destruye también el volumen, perdiendo la información de la base de datos permanentemente.

¿Por qué web alcanza a db sin publicar el puerto 5432?
Porque Docker Compose crea una red interna (s04_default) con su propio DNS. Dentro de esta red, los contenedores tienen todos sus puertos expuestos entre sí de forma privada y se encuentran por su nombre de servicio, sin necesidad de publicar el puerto hacia la máquina host.

¿Qué pasaría si el .env estuviera dentro de la imagen?
La contraseña quedaría "horneada" en el código fuente de la imagen. Cualquiera con acceso a la imagen podría ver las credenciales, y tendrías que reconstruir la imagen entera (compilarla de nuevo) solo para cambiar de un entorno de pruebas a uno de producción.