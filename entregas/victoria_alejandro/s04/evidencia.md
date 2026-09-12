#### Misión 1 ####
1.1 NO hay volumen
Comenté las líneas que hacen referencia al volumen en el archivo compose.yaml
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:17-alpine
    env_file: .env
    #volumes:
    #  - datos_banco:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  cache:
    image: redis:8-alpine

  broker:
    image: rabbitmq:4-management-alpine
    ports:
      - "15672:15672"

#volumes:
#  datos_banco:

###Salida###
(base) alex@Apples-MacBook-Pro s04 % docker compose up -d
[+] Running 5/5
 ✔ Network s04_default     Created                                                                                                              0.2s 
 ✔ Container s04-db-1      Healthy                                                                                                             12.9s 
 ✔ Container s04-broker-1  Started                                                                                                              2.8s 
 ✔ Container s04-cache-1   Started                                                                                                              2.6s 
 ✔ Container s04-web-1     Started                                                                                                             13.0s 
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "CREATE TABLE usuarios(id int primary key, nombre varchar(10), edad int);"
CREATE TABLE
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "INSERT INTO usuarios VALUES (1, 'Ale',22);"

INSERT 0 1
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "SELECT * FROM usuarios;"

 id | nombre | edad 
----+--------+------
  1 | Ale    |   22
(1 row)

(base) alex@Apples-MacBook-Pro s04 % docker compose down
[+] Running 5/5
 ✔ Container s04-broker-1  Removed                                                                                                              1.8s 
 ✔ Container s04-cache-1   Removed                                                                                                              0.5s 
 ✔ Container s04-web-1     Removed                                                                                                              0.6s 
 ✔ Container s04-db-1      Removed                                                                                                              0.4s 
 ✔ Network s04_default     Removed                                                                                                              0.3s 
(base) alex@Apples-MacBook-Pro s04 % docker compose up -d                                                  
[+] Running 5/5
 ✔ Network s04_default     Created                                                                                                              0.2s 
 ✔ Container s04-cache-1   Started                                                                                                              2.1s 
 ✔ Container s04-broker-1  Started                                                                                                              2.1s 
 ✔ Container s04-db-1      Healthy                                                                                                             12.5s 
 ✔ Container s04-web-1     Started                                                                                                             12.5s 
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "SELECT * FROM usuarios;"

ERROR:  relation "usuarios" does not exist
LINE 1: SELECT * FROM usuarios;

1.2 SÍ hay volumen
Ahora ya descomenté las líneas que hacen referencia al volumen del archivo compose.yaml 
(base) alex@Apples-MacBook-Pro s04 % docker compose down
[+] Running 5/5
 ✔ Container s04-web-1     Removed                                                                                                              0.4s 
 ✔ Container s04-cache-1   Removed                                                                                                              0.4s 
 ✔ Container s04-broker-1  Removed                                                                                                              1.6s 
 ✔ Container s04-db-1      Removed                                                                                                              0.5s 
 ✔ Network s04_default     Removed                                                                                                              0.3s 
(base) alex@Apples-MacBook-Pro s04 % docker compose up -d                                                  
[+] Running 6/6
 ✔ Network s04_default       Created                                                                                                            0.2s 
 ✔ Volume "s04_datos_banco"  Created                                                                                                            0.0s 
 ✔ Container s04-broker-1    Started                                                                                                            2.7s 
 ✔ Container s04-cache-1     Started                                                                                                            2.4s 
 ✔ Container s04-db-1        Healthy                                                                                                            8.1s 
 ✔ Container s04-web-1       Started                                                                                                            8.1s 
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "CREATE TABLE usuarios(id int primary key, nombre varchar(10), edad int);"
CREATE TABLE
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "INSERT INTO usuarios VALUES (1, 'Ale',22);"

INSERT 0 1
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "SELECT * FROM usuarios;"

 id | nombre | edad 
----+--------+------
  1 | Ale    |   22
(1 row)

(base) alex@Apples-MacBook-Pro s04 % docker compose down 
[+] Running 5/5
 ✔ Container s04-web-1     Removed                                                                                                              0.5s 
 ✔ Container s04-broker-1  Removed                                                                                                              1.7s 
 ✔ Container s04-cache-1   Removed                                                                                                              0.3s 
 ✔ Container s04-db-1      Removed                                                                                                              0.5s 
 ✔ Network s04_default     Removed                                                                                                              0.4s 
(base) alex@Apples-MacBook-Pro s04 % docker compose up -d
[+] Running 5/5
 ✔ Network s04_default     Created                                                                                                              0.2s 
 ✔ Container s04-broker-1  Started                                                                                                              2.3s 
 ✔ Container s04-db-1      Healthy                                                                                                              7.5s 
 ✔ Container s04-cache-1   Started                                                                                                              2.4s 
 ✔ Container s04-web-1     Started                                                                                                              7.5s 
(base) alex@Apples-MacBook-Pro s04 % docker exec -it s04-db-1 psql -U postgres -c "SELECT * FROM usuarios;"                   

 id | nombre | edad 
----+--------+------
  1 | Ale    |   22
(1 row)
### Misión 2 ###
El contenedor de la misión anterior sigue arriba y a ese me voy a intentar conectar
(base) alex@Apples-MacBook-Pro s04 % docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
(base) alex@Apples-MacBook-Pro s04 % docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections

### Misión 3 ###
Para esta misión encontré que puedo evitar que se suban todos los archivos .env a git utilizando el *
Entonces mi archivo .gitignore contiene la línea .env*
(base) alex@Apples-MacBook-Pro s04 % docker run --rm --env-file .env postgres:17-alpine env | grep POSTGRES 
POSTGRES_DATABASE=banco_postgres
POSTGRES_CONTRA=contraseña
POSTGRES_SALUDO=hola
(base) alex@Apples-MacBook-Pro s04 % docker run --rm --env-file .env.prod postgres:17-alpine env | grep POSTGRES 
POSTGRES_DATABASE=banco_postgres
POSTGRES_CONTRA=secreto_db2
POSTGRES_SALUDO=hola

### Misión 4 ###
(base) alex@Apples-MacBook-Pro s04 % docker compose up -d
[+] Running 5/5
 ✔ Network s04_default                Created                                                                                                   0.2s 
 ✔ Volume "s04_datos_banco_practica"  Created                                                                                                   0.0s 
 ✔ Container s04-cache-1              Started                                                                                                   2.0s 
 ✔ Container s04-db-1                 Started                                                                                                   1.9s 
 ✔ Container s04-web-1                Started                                                                                                   2.6s 
(base) alex@Apples-MacBook-Pro s04 % docker compose ps
NAME          IMAGE            COMMAND                  SERVICE   CREATED          STATUS         PORTS
s04-cache-1   redis:8-alpine   "docker-entrypoint.s…"   cache     12 seconds ago   Up 7 seconds   6379/tcp
s04-web-1     nginx:alpine     "/docker-entrypoint.…"   web       11 seconds ago   Up 7 seconds   0.0.0.0:8081->80/tcp

¿Qué se pierde con down y qué con down -v?
Con down solo se eliminan los contenedores y las redes que se crearon cuando se levantó el contenedor. Con down -v se eliminan contenedores, redes y los volúmenes; no queda nada.

¿Por qué web alcanza a db sin publicar el puerto 5432?
No se necesitan comunicar por medio del puerto porque, al crearse la red por default, los servicios se pueden comunicar utilizando sus nombres de dominio que resuelve el DNS interno del contenedor.

¿Qué pasaría si el .env estuviera dentro de la imagen?
Perdería su sentido porque al crear un contenedor no se podrían configurar las variables del entorno y si todo el mundo tiene la misma contraseña ya no hay seguridad. 