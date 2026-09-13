\# Evidencia S04 · Docker, día dos

\## Melissa Saucedo



\## Comandos que usé



docker network create redlab

docker run -d --name web --network redlab nginx:alpine

docker run --rm --network redlab alpine:3.20 wget -qO- http://web

docker run --rm alpine:3.20 wget -qO- http://web

docker network inspect redlab

docker run -d --name db1 -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"

docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

docker rm -f db1

docker run -d --name db1 -v datos\_banco:/var/lib/postgresql/data -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

docker run -d --name db\_dev --network redlab -e POSTGRES\_PASSWORD=dev -e POSTGRES\_DB=banco\_dev postgres:17-alpine

docker run -d --name db\_prod --network redlab -e POSTGRES\_PASSWORD=prod -e POSTGRES\_DB=banco\_prod postgres:17-alpine

docker exec db\_dev env | grep POSTGRES

docker exec db\_prod env | grep POSTGRES

docker run --rm --env-file .env postgres:17-alpine env | grep POSTGRES

docker compose up -d

docker compose ps

docker compose logs db

docker compose exec db psql -U postgres -c "\\l"

docker compose exec cache redis-cli ping

docker compose exec web sh

docker compose config

docker network inspect s04\_default

docker run --rm --network s04\_default postgres:17-alpine pg\_isready -h db

docker run --rm postgres:17-alpine pg\_isready -h db



\## Misión 1 · Persistencia



\### Sin volumen: la tabla no sobrevive



CREATE TABLE

INSERT 0 1

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)



Después de docker rm -f db1 y recrearlo con el mismo comando (sin volumen):



ERROR:  relation "cuentas" does not exist

LINE 1: SELECT \* FROM cuentas;

&#x20;                     ^



\### Con volumen: la fila sobrevive



Después de docker rm -f db1 y recrearlo con -v datos\_banco:/var/lib/postgresql/data:



&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)



\## Misión 2 · Red



Dentro de la red del proyecto (s04\_default):



docker run --rm --network s04\_default postgres:17-alpine pg\_isready -h db

db:5432 - accepting connections



Fuera de la red:



docker run --rm postgres:17-alpine pg\_isready -h db

db:5432 - no response



\## Misión 3 · Configuración



Misma imagen (postgres:17-alpine), dos configuraciones distintas:



docker exec db\_dev env | grep POSTGRES

POSTGRES\_PASSWORD=dev

POSTGRES\_DB=banco\_dev



docker exec db\_prod env | grep POSTGRES

POSTGRES\_DB=banco\_prod

POSTGRES\_PASSWORD=prod



En el repo: .env está en .gitignore, y .env.example trae los nombres de las variables con valores falsos.



\## Misión 4 · Compose



docker compose ps



NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS

s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    30 seconds ago   Up 28 seconds             0.0.0.0:15672->15672/tcp, \[::]:15672->15672/tcp

s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     30 seconds ago   Up 28 seconds             6379/tcp

s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        30 seconds ago   Up 28 seconds (healthy)   5432/tcp

s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       29 seconds ago   Up 23 seconds             0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp



\### Preguntas



\*\*¿Qué se pierde con down y qué con down -v?\*\*

Con docker compose down se borran los contenedores y la red, pero el volumen datos\_banco se queda, así que si vuelvo a hacer up, los datos siguen ahí. Con docker compose down -v también se borra el volumen, y ahí sí se pierden los datos para siempre, es como cuando probé sin volumen al inicio del lab.



\*\*¿Por qué web alcanza a db sin publicar el puerto 5432?\*\*

Porque web y db están en la misma red interna que crea Compose (s04\_default), y ahí los contenedores se encuentran por su nombre de servicio, como vi con web y redlab en el ejercicio 2. Publicar un puerto es distinto: eso es para que yo, desde mi navegador fuera de Docker, pueda llegar al contenedor. Como db nunca necesita que algo de fuera de Docker lo toque, no hace falta publicarlo, solo web, que sí lo consulta desde dentro de la misma red.



\*\*¿Qué pasaría si el .env estuviera dentro de la imagen?\*\*

Si el .env estuviera dentro de la imagen, la contraseña quedaría fija y para cambiarla tendría que reconstruir la imagen completa. Además ya no podría usar la misma imagen para dev y producción con distinta configuración, como hice con db\_dev y db\_prod. Y sería un riesgo de seguridad: cualquiera que tuviera la imagen podría ver la contraseña, aunque el repo de código estuviera bien protegido.

