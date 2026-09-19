# Evidencia Javier Velasco· Sesión 04: Estado, red, configuración y compose

## Misión 1. Persistencia


### 1. Sin Volumen
Después de docker rm -f db1, dejan de existir los datos
```bash
D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker rm -f db1
db1

D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
a8001ffbf9fd56f91d9e2d6561cfe2a3657c19e95d7a9b6fc786bb8fbc4ed0b5

D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
```
### 2. Con Volumen
Los datos persisten en el volumen tras eliminar db1.
```bash
D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker rm -f db1
db1

D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
f71831b5c6c21f10ff0165d9d7a2da3cbbc24d46c11b2b5f8239781c3285879c

D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)
```
## Misión 2. Red
Docker permite que un contenedor encuentre a db por nombre solamente cuando ambos están en la misma red Docker.

En la misma red 
```bash
D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
```
No se tiene a db en la misma red
```bash
D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response
```

## Mision 3. Configuración
findstr debido a uso de Windows que no me permitio usar grep. Se creó un archivo .env para guardar las variables de configuración de POSTGRES
### db_dev
```bash
D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker run -d --name db_dev --network redlab -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=banco_dev postgres:17-alpine
73d9d6c97fadeec2f3654d1e7adce71c4c2ad19ea117c8d4066c33e4ee5b76ee

D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker exec db_dev env | findstr POSTGRES
POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
```
### db_prod
```bash
D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker run -d --name db_prod --network redlab -e POSTGRES_PASSWORD=prod -e POSTGRES_DB=banco_prod postgres:17-alpine
b47bd9aa4230e8c5100b56dff90639768d3e063f0880834021e998b4d34a2706

D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker exec db_prod env | findstr POSTGRES
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
```

## Mision 4. Compose
```bash
D:\GitHub\sd-2027-1\entregas\velasco_javier\s04>docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED         STATUS                   PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    7 minutes ago   Up 7 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     7 minutes ago   Up 7 minutes             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        2 minutes ago   Up 2 minutes (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       7 minutes ago   Up 2 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

## PREGUNTAS
#### 1. ¿Qué se pierde con down y qué con down -v?
Docker compose down, lo único que haces es eliminar los contenedores y red, pero la información se queda a salvo por el volumen que esta en el disco. En cambio, down -v, le estás indicando a Docker que también borre los volúmenes asociados, lo que significa que se pierde todo la informacion.

#### 2. ¿Por qué web alcanza a db sin publicar el puerto 5432?
Esto pasa porque Docker Compose crea una red privada para todos los contenedores. Como la app web y la base de datos están en esa misma red, se pueden hablar directamente usando solo el nombre, sin necesidad de abrir el puerto lo que es mucho más seguro. Publicar un puerto solo es necesario cuando nosotros necesitamos acceder a ese servicio fuera de nuestra computadora.

#### 3. ¿Qué pasaría si el .env estuviera dentro de la imagen?
Si el archivo .env con las credenciales estuviera metido dentro de la imagen de Docker, las contraseñas quedarían expuestas dentro del código del contenedor lo cual es una riesgo enorme de que alguien robe información sensible si el repositorio.