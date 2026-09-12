# Misión 1 - Persistencia

-- Sin volumen--

docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
docker exec -it db1 psql -U postgres -c "CREATE TABLE amigos(id int primary key, nombre text);"
docker exec -it db1 psql -U postgres -c "INSERT INTO amigos VALUES (1, 'Kevin');"
docker exec -it db1 psql -U postgres -c "SELECT * FROM amigos;"

Salida:

```text
CREATE TABLE
INSERT 0 1

 id | nombre
----+--------
  1 | Kevin
(1 row)
```

Se elimina y se recrea el contenedor:

docker rm -f db1
docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
docker exec -it db1 psql -U postgres -c "SELECT * FROM amigos;"

Salida:

```text
ERROR: relation "amigos" does not exist
LINE 1: SELECT * FROM amigos;
                      ^
```

Sin volumen, los datos se pierden al eliminar el contenedor.

-- Con volumen --

docker rm -f db1
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
docker exec -it db1 psql -U postgres -c "CREATE TABLE amigos(id int primary key, nombre text);"
docker exec -it db1 psql -U postgres -c "INSERT INTO amigos VALUES (1, 'Kevin');"
docker exec -it db1 psql -U postgres -c "SELECT * FROM amigos;"

Salida:

```text
CREATE TABLE
INSERT 0 1

 id | nombre
----+--------
  1 | Kevin
(1 row)
```

Se elimina y se recrea usando el mismo volumen:

docker rm -f db1
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
docker exec -it db1 psql -U postgres -c "SELECT * FROM amigos;"

Salida:

```text
 id | nombre
----+--------
  1 | Kevin
(1 row)
```

## Misión 2 - Red

Con el sistema levantado:

docker compose up -d
docker compose ps

Probamos el acceso a db desde otro contenedor dentro de la misma red

docker run --rm --network s04_default postgres:17-alpine pg_isready -h db

Salida:

```text
db:5432 - accepting connections
```

Después repetimos la prueba sin conectarlo a `s04_default`:

docker run --rm postgres:17-alpine pg_isready -h db

Salida:

```text
db:5432 - no response
```

## Misión 3 - Configuración

Se utiliza la misma imagen con dos archivos de configuración distintos:

docker run -d --name db_dev --env-file .env postgres:17-alpine
docker run -d --name db_prod --env-file .env.prod postgres:17-alpine

Configuración de db_dev:

docker exec db_dev env | grep POSTGRES

```text
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco_dev
```

Configuración de db_prod:

docker exec db_prod env | grep POSTGRES

```text
POSTGRES_PASSWORD=produccion
POSTGRES_DB=banco_prod
```

## Misión 4 - Compose


docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"

```text
NAME           SERVICE   STATUS                       PORTS
s04-broker-1   broker    Up About an hour             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up About an hour             6379/tcp
s04-db-1       db        Up About an hour (healthy)   5432/tcp
s04-web-1      web       Up About an hour             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

-- Preguntas --

*¿Qué se pierde con down y qué con down -v?
down elimina los contenedores y la red pero conserva el volumen. docker compose down -v también elimina el volumen.

*¿Por qué web alcanza a db sin publicar el puerto 5432?
Porque Compose crea una red interna para el proyecto y los servicios pueden comunicarse usando sus nombres. Por eso web puede encontrar a db directamente dentro de s04_default.

*¿Qué pasaría si el .env estuviera dentro de la imagen?
La configuración y las credenciales quedarían dentro de la imagen, y se podrían exponer datos sensibles.

