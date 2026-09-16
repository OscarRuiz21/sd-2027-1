# Evidencia S04 · Docker: volúmenes, redes, variables de entorno y Compose

**Estudiante:** Victoria Muñoz  
**Carpeta:** `entregas/munoz\\\_victoria/s04`  
**Rama:** `entregas\\\_munoz\\\_victoria`

\---

## Misión 1 · Persistencia con volúmenes

### Sin volumen

Se creó un contenedor PostgreSQL sin volumen:

```bash
docker run -d --name db1 -e POSTGRES\\\_PASSWORD=pepocumbia7 postgres:17-alpine
```

Se creó la tabla, se insertó una fila y se consultó:

```bash
winpty docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
winpty docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
winpty docker exec -it db1 psql -U postgres -c "SELECT \\\* FROM cuentas;"
```

Salida:

```text
CREATE TABLE
INSERT 0 1

 id | saldo
----+-------
  1 |   100
(1 row)
```

Después se eliminó el contenedor y se creó nuevamente sin volumen:

```bash
docker rm -f db1
docker run -d --name db1 -e POSTGRES\\\_PASSWORD=pepocumbia7 postgres:17-alpine
winpty docker exec -it db1 psql -U postgres -c "SELECT \\\* FROM cuentas;"
```

Resultado:

```text
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT \\\* FROM cuentas;
```

**Conclusión:** sin un volumen, los datos almacenados en la capa de escritura del contenedor se pierden al eliminarlo.

### Con volumen

Se creó el contenedor usando un volumen nombrado:

```bash
docker rm -f db1
docker run -d --name db1 -v datos\\\_banco:/var/lib/postgresql/data -e POSTGRES\\\_PASSWORD=secreto postgres:17-alpine
```

Se creó la tabla y se insertó una fila:

```bash
winpty docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
winpty docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
```

Después se eliminó y recreó el contenedor usando el mismo volumen:

```bash
docker rm -f db1
docker run -d --name db1 -v datos\\\_banco:/var/lib/postgresql/data -e POSTGRES\\\_PASSWORD=secreto postgres:17-alpine
winpty docker exec -it db1 psql -U postgres -c "SELECT \\\* FROM cuentas;"
```

Salida:

```text
 id | saldo
----+-------
  1 |   100
(1 row)
```

También se comprobó la existencia del volumen:

```bash
docker volume ls | grep datos\\\_banco
```

Salida:

```text
local    datos\\\_banco
local    s04\\\_datos\\\_banco
```

**Conclusión:** el contenedor se eliminó, pero el volumen permaneció y permitió recuperar los datos.

\---

## Misión 2 · Red y descubrimiento por nombre

Para validar la capacidad de resolución de DNS interno dentro de la infraestructura de Docker Compose, se realizaron pruebas de conectividad utilizando la utilidad `pg\_isready` sobre el servicio `db`.

```

docker compose up -d

\[+] up 4/4

&#x20;✔ Container s04-cache-1  Started                                                               0.3s

&#x20;✔ Container s04-broker-1 Started                                                               0.6s

&#x20;✔ Container s04-db-1     Healthy                                                               5.8s

&#x20;✔ Container s04-web-1    Started                                                               0.4s

```

En la primera prueba, se ejecutó un contenedor efímero incorporándolo explícitamente a la red predeterminada del proyecto (`s04\_default`):

```bash

docker run --rm --network s04\_default postgres:17-alpine pg\_isready -h db

```

Como resultado, el sistema logró resolver el nombre del host gracias al puente de red, obteniendo la siguiente respuesta:

```

db:5432 - accepting connections

```

Posteriormente, se repitió la misma prueba de validación, pero omitiendo la conexión a la red de red privada (s04\_default):

```Bash

docker run --rm postgres:17-alpine pg\_isready -h db

```

Al carecer de visibilidad sobre la red del clúster, el motor de contenedores fue incapaz de ubicar el destino, arrojando el fallo esperado:

```

db:5432 - no response

```

Conclusión técnica: Esta experimentación corrobora que las redes definidas en Docker Compose proveen un entorno aislado de resolución de nombres. Los contenedores acoplados a dicha red pueden localizarse mutuamente mediante su identificador de servicio, mientras que cualquier proceso externo queda incomunicado por seguridad.

\---

## Misión 3 · Configuración mediante variables de entorno

Se generaron los archivos de configuración con sus respectivas credenciales:

```bash

echo -e "POSTGRES\_PASSWORD=secreto\\nPOSTGRES\_DB=banco" > .env

echo -e "POSTGRES\_PASSWORD=produccion123\\nPOSTGRES\_DB=banco\_prod" > .env.prod

```

Posteriormente, se instanciaron dos contenedores independientes a partir de la misma imagen de PostgreSQL, inyectando los parámetros mediante la directiva --env-file:

```Bash

docker run -d --name db\_dev --env-file .env postgres:17-alpine

7e8211a5b2373a58d77b8ad9a8052455757e43c8b2e6ee18e196961ebc50f305

docker run -d --name db\_prod --env-file .env.prod postgres:17-alpine

c624fd80e308251b45d6f6a557ac9f2fc827a14dcaf18a1e0352e06202d313bd

```

Para validar la correcta recepción de los parámetros, se consultaron las variables de entorno internas de cada instancia:

```Bash

docker exec db\_dev env | grep POSTGRES

docker exec db\_prod env | grep POSTGRES

```

Obteniendo las siguientes salidas diferenciadas:

```

POSTGRES\_PASSWORD=secreto

POSTGRES\_DB=banco

POSTGRES\_PASSWORD=produccion123

POSTGRES\_DB=banco\_prod

```

Finalmente, se procedió a limpiar los recursos temporales utilizados en la prueba:

```Bash

docker rm -f db\_dev db\_prod

```

Buenas prácticas de seguridad implementadas:

Los archivos que contienen secretos confidenciales (.env y .env.prod) han sido excluidos del control de versiones mediante su incorporación en el archivo .gitignore.

En su lugar, se provee una plantilla pública denominada .env.example la cual contiene exclusivamente valores simulados o genéricos necesarios para documentar la estructura requerida por el proyecto.

\---

## Misión 4 · Docker Compose

Se creó un `compose.yaml` con cuatro servicios:

* `web` → nginx
* `db` → PostgreSQL
* `cache` → Redis
* `broker` → RabbitMQ

Configuración utilizada:

```yaml
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
    depends\\\_on:
      db:
        condition: service\\\_healthy

  db:
    image: postgres:17-alpine
    env\\\_file: .env
    volumes:
      - datos\\\_banco:/var/lib/postgresql/data
    healthcheck:
      test: \\\["CMD-SHELL", "pg\\\_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  cache:
    image: redis:8-alpine

  broker:
    image: rabbitmq:4-management-alpine
    ports:
      - "15672:15672"

volumes:
  datos\\\_banco:
```

Se levantó el sistema con:

```bash
docker compose up -d
```

Resultado:

```text
\\\[+] up 5/5
 ✔ Network s04\\\_default       Created
 ✔ Container s04-db-1       Healthy
 ✔ Container s04-cache-1    Started
 ✔ Container s04-broker-1   Started
 ✔ Container s04-web-1      Started
```

Se verificaron los servicios:

```bash
docker compose ps --format "table {{.Name}}	{{.Service}}	{{.Status}}	{{.Ports}}"
```

Salida:

```text
NAME           SERVICE   STATUS                   PORTS
s04-broker-1   broker    Up 9 seconds             0.0.0.0:15672->15672/tcp, \\\[::]:15672->15672/tcp
s04-cache-1    cache     Up 9 seconds             6379/tcp
s04-db-1       db        Up 9 seconds (healthy)   5432/tcp
s04-web-1      web       Up 3 seconds             0.0.0.0:8080->80/tcp, \\\[::]:8080->80/tcp
```

Se verificó la configuración resuelta:

```bash
docker compose config
```

Compose mostró, entre otros datos:

```text
POSTGRES\\\_DB: banco
image: postgres:17-alpine
source: datos\\\_banco
target: /var/lib/postgresql/data
name: s04\\\_default
name: s04\\\_datos\\\_banco
```

Se comprobó que PostgreSQL tiene la base `banco`:

```bash
docker compose exec db psql -U postgres -c "\\\\l"
```

Salida relevante:

```text
banco     | postgres | UTF8
postgres  | postgres | UTF8
template0 | postgres | UTF8
template1 | postgres | UTF8
```

Se comprobó Redis:

```bash
docker compose exec cache redis-cli ping
```

Resultado:

```text
PONG
```

\---

## Preguntas de la Misión 4

### 1\. ¿Qué se pierde con `docker compose down` y qué con `docker compose down -v`?

`docker compose down` elimina los contenedores y la red del proyecto, pero conserva los volúmenes. Por lo tanto, los datos de PostgreSQL permanecen.

`docker compose down -v` también elimina los volúmenes del proyecto. Al eliminar el volumen `s04\\\_datos\\\_banco`, se pierden los datos almacenados en él.

### 2\. ¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?

Porque Compose crea automáticamente una red para el proyecto y conecta todos los servicios a esa red. Dentro de esa red, los servicios se pueden encontrar mediante su nombre de servicio.

Por eso `web` puede comunicarse con `db:5432` aunque el puerto 5432 de PostgreSQL no esté publicado en la máquina host.

### 3\. ¿Qué pasaría si el `.env` estuviera dentro de la imagen?

La configuración y las credenciales quedarían incorporadas a la imagen. Esto sería inseguro, porque cualquiera que tenga acceso a la imagen podría obtener información que debería mantenerse fuera de ella.

La configuración debe mantenerse fuera de la imagen y proporcionarse mediante variables de entorno o archivos de configuración externos. El archivo `.env` tampoco debe subirse al repositorio.

