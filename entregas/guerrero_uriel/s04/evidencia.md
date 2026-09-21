Evidencia S04 - Docker

Misión 1. Persistencia

Caso 1: Sin volumen (Demostración de pérdida de datos)

Primero creamos y levantamos el contenedor de PostgreSQL reto_db sin ningún volumen montado:

ur182@MacBook-Air-de-Uriel s04 % docker run -d --name reto_db -e POSTGRES_PASSWORD=secreto postgres:17-alpine
75310ee9a50d4b33bf645c4726704cb68eee5023f7fa4759a49aad11f1ba3553


A continuación, creamos la tabla cuentas e insertamos un registro:

ur182@MacBook-Air-de-Uriel s04 % docker exec -it reto_db psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

ur182@MacBook-Air-de-Uriel s04 % docker exec -it reto_db psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1


Verificamos que la tabla contiene el registro insertado:

ur182@MacBook-Air-de-Uriel s04 % docker exec -it reto_db psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo 
----+-------
  1 |   100
(1 row)


Ahora eliminamos forzadamente el contenedor para probar la volatilidad de los datos:

ur182@MacBook-Air-de-Uriel s04 % docker rm -f reto_db
reto_db


Si volvemos a levantar un contenedor completamente nuevo sin volumen y consultamos la tabla:

ur182@MacBook-Air-de-Uriel s04 % docker run -d --name reto_db -e POSTGRES_PASSWORD=secreto postgres:17-alpine
ur182@MacBook-Air-de-Uriel s04 % docker exec -it reto_db psql -U postgres -c "SELECT * FROM cuentas;"


Obtenemos el siguiente error que confirma que la información se perdió por completo al eliminar el contenedor anterior:

ERROR: relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                       ^


Caso 2: Con volumen (Demostración de persistencia de datos)

Para asegurar la persistencia, creamos un volumen nombrado llamado datos_banco y vinculamos la ruta de datos de PostgreSQL:

ur182@MacBook-Air-de-Uriel s04 % docker volume create datos_banco
datos_banco
ur182@MacBook-Air-de-Uriel s04 % docker run -d --name reto_db -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine


Creamos nuevamente la tabla e insertamos el registro:

ur182@MacBook-Air-de-Uriel s04 % docker exec -it reto_db psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE
ur182@MacBook-Air-de-Uriel s04 % docker exec -it reto_db psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1


Destruimos el contenedor actual con docker rm -f:

ur182@MacBook-Air-de-Uriel s04 % docker rm -f reto_db
reto_db


Levantamos un nuevo contenedor conectándole el mismo volumen datos_banco:

ur182@MacBook-Air-de-Uriel s04 % docker run -d --name reto_db -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine


Al consultar la tabla nuevamente:

ur182@MacBook-Air-de-Uriel s04 % docker exec -it reto_db psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo 
----+-------
  1 |   100
(1 row)


Comprobamos que el registro permanece intacto gracias al volumen montado.

Misión 2. Red

Comprobamos la conectividad entre contenedores dentro de la red administrada por Compose (s04_default).

Primero probamos la comunicación desde un contenedor en la misma red resolviendo el nombre del servicio db:

ur182@MacBook-Air-de-Uriel s04 % docker run --rm --network s04_default busybox nc -zv db 5432
db:5432 - accepting connections


Posteriormente realizamos la prueba desde un contenedor fuera de la red s04_default:

ur182@MacBook-Air-de-Uriel s04 % docker run --rm busybox nc -zv db 5432
db:5432 - no response


Esto demuestra que la resolución de nombres entre servicios funciona exclusivamente dentro de la red compartida definida en Compose.

Misión 3. Configuración

Se probaron dos archivos de variables de entorno diferentes con la misma imagen de PostgreSQL.

Para el entorno de desarrollo usando .env:

ur182@MacBook-Air-de-Uriel s04 % docker run --rm --env-file .env postgres:17-alpine printenv | grep POSTGRES
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco


Para el entorno de producción usando .env.prod:

ur182@MacBook-Air-de-Uriel s04 % docker run --rm --env-file .env.prod postgres:17-alpine printenv | grep POSTGRES
POSTGRES_PASSWORD=produccion123
POSTGRES_DB=banco_prod


Los archivos de secretos (.env y .env.prod) se incluyeron en el .gitignore para evitar filtraciones en el repositorio git, manteniendo únicamente un archivo .env.example como plantilla.

Misión 4. Compose

Definimos el archivo compose.yaml con los servicios web, db, cache y broker.

Verificamos el estado de los servicios en ejecución:

ur182@MacBook-Air-de-Uriel s04 % docker compose ps
NAME            IMAGE                        COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04-broker-1    rabbitmq:4-management-alpine "docker-entrypoint.s…"   broker    16 minutes ago   Up 16 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1     redis:8-alpine                  "docker-entrypoint.s…"   cache     16 minutes ago   Up 16 minutes             6379/tcp
s04-db-1        postgres:17-alpine              "docker-entrypoint.s…"   db        16 minutes ago   Up 16 minutes (healthy)   5432/tcp
s04-web-1       nginx:alpine                    "/docker-entrypoint.…"   web       16 minutes ago   Up 16 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp


Respuestas a las preguntas de reflexión

¿Qué se pierde con down vs down -v?

docker compose down: Elimina los contenedores, redes e imágenes creadas temporalmente por el archivo Compose, pero conserva los volúmenes nombrados, asegurando que los datos persistentes no se borren.

docker compose down -v: Elimina absolutamente todo, incluyendo los volúmenes nombrados declarados en la sección volumes. Todos los datos almacenados en las bases de datos se perderán de manera permanente.

¿Por qué web llega a db sin publicar el puerto 5432?

Porque Docker Compose crea automáticamente una red bridge interna compartida por todos los servicios del archivo. Dentro de esta red interna, el contenedor web puede resolver e interactuar con db a través de su puerto interno 5432 usando el nombre del servicio como dirección IP virtual. La publicación de puertos (ports:) solo es necesaria si se quiere acceder al servicio desde la máquina host (tu computadora).

¿Qué pasa si .env está dentro de la imagen?

Si incrustas archivos de entorno o claves secretas directamente dentro de las capas de la imagen Docker:

Riesgo de seguridad: Cualquier persona con permiso para descargar o inspect de la imagen (docker inspect o docker history) podrá ver las credenciales en texto plano.

Falta de flexibilidad: La imagen pierde portabilidad, ya que no podrías reutilizar el mismo contenedor para entornos de desarrollo, pruebas y producción sin volver a construir la imagen.