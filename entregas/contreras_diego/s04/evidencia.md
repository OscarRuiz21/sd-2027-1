Evidencia de Entrega



&#x20;Misión 1 Persistencia



&#x20;Con Volumen (Sobrevive)



&#x20;Crear contenedor con volumen nombrado

&#x20;docker run -d --name db\_vol -v datos\_test:/var/lib/postgresql/data -e POSTGRES\_PASSWORD=secreto postgres:17-alpine



&#x20;Insertar un registro de prueba

&#x20;docker exec -it db\_vol psql -U postgres -c "CREATE TABLE test(id int); INSERT INTO test VALUES (1);"



&#x20;Eliminar contenedor forzadamente

&#x20;docker rm -f db\_vol



&#x20;Volver a crear el contenedor usando el mismo volumen

&#x20;docker run -d --name db\_vol -v datos\_test:/var/lib/postgresql/data -e POSTGRES\_PASSWORD=secreto postgres:17-alpine



&#x20;Consultar los datos

&#x20;docker exec -it db\_vol psql -U postgres -c "SELECT \* FROM test;



&#x20;Salida:

&#x20; id

&#x20;----

&#x20;  1

&#x20;(1 row)



&#x20;Sin Volumen (No Sobrevive)



&#x20;Crear contenedor sin volumen:

&#x20;docker run -d --name db\_sin\_vol -e POSTGRES\_PASSWORD=secreto postgres:17-alpine



&#x20;Insertar un registro de prueba:

&#x20;docker exec -it db\_sin\_vol psql -U postgres -c "CREATE TABLE test(id int); INSERT INTO test VALUES (1);"



&#x20;Eliminar contenedor forzadamente:

&#x20;docker rm -f db\_sin\_vol



&#x20;Volver a crear el contenedor sin volumen:

&#x20;docker run -d --name db\_sin\_vol -e POSTGRES\_PASSWORD=secreto postgres:17-alpine



&#x20;Intentar consultar la tabla borrada (genera error):

&#x20;docker exec -it db\_sin\_vol psql -U postgres -c "SELECT \* FROM test;"



&#x20;Salida (ERROR):

&#x20;ERROR: relation "test" does not exist

&#x20;LINE 1: SELECT \* FROM test;



&#x20;Mision 2 Red



&#x20;Dentro de la Red

&#x20;docker run --rm --network s04\_default postgres:17-alpine pg\_isready -h db



&#x20;Salida:

&#x20;db:5432 - accepting connections



&#x20;Fuera de la Red

&#x20;docker run --rm postgres:17-alpine pg\_isready -h db



&#x20;Salida (ERROR):

&#x20;db:5432 - no response



&#x20;Mision 3 Configuracion



&#x20;Instancia 1 con .env:

&#x20;docker run -d --name app\_dev --env-file .env postgres:17-alpine

&#x20;docker exec app\_dev env



&#x20;Instancia 2 con .env.prod:

&#x20;docker run -d --name app\_prod --env-file .env.prod postgres:17-alpine

&#x20;docker exec app\_prod env



&#x20;Salida app\_dev (.env):

&#x20;POSTGRES\_PASSWORD=secreto

&#x20;POSTGRES\_DB=banco



&#x20;Salida app\_prod (.env.prod):

&#x20;POSTGRES\_PASSWORD=produccion\_segura\_987

&#x20;POSTGRES\_DB=banco\_prod



&#x20;Mision 4 Compose



&#x20;Estado del sistema (docker compose ps):

&#x20;NAME         IMAGE                      COMMAND                  SERVICE   CREATED         STATUS                   PORTS

&#x20;s04-broker-1 rabbitmq:4-management-...  "docker-entrypoint.s…"   broker    10 minutes ago  Up 10 minutes            0.0.0.0:15672->15672/tcp

&#x20;s04-cache-1  redis:8-alpine             "docker-entrypoint.s…"   cache     10 minutes ago  Up 10 minutes            6379/tcp

&#x20;s04-db-1     postgres:17-alpine         "docker-entrypoint.s…"   db        10 minutes ago  Up 10 minutes (healthy)  5432/tcp

&#x20;s04-web-1    nginx:alpine               "/docker-entrypoint.…"   web       10 minutes ago  Up 10 minutes            0.0.0.0:8080->80/tcp



&#x20;PREGUNTAS



&#x20;1. ¿Que se pierde con docker compose down y que con docker compose down -v?

&#x20;   docker compose down: Detiene y elimina contenedores y la red creada

&#x20;   docker compose down -v: Elimina todo lo anterior mas los volumenes nombrados y sus datos persistentes



&#x20;2. ¿Por que web alcanza a db sin publicar el puerto 5432?

&#x20;   Porque dentro de la red interna de Docker todos los contenedores expuestos en esa red se comunican directamente usando sus puertos internos, publicar con ports solo se utiliza para exponer el puerto a la maquina host.



&#x20;3. ¿Que pasaria si el .env estuviera dentro de la imagen?

&#x20;   Se perderia la seguridad y la portabilidad.

