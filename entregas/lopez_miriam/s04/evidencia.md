# Evidencia S04

En esta práctica trabajé con volúmenes, redes, variables de entorno y Docker Compose. Fui haciendo varias pruebas para ver qué pasa con los datos, cómo se comunican los contenedores y cómo se puede cambiar la configuración sin modificar la imagen.

## Misión 1 - Persistencia

Primero levanté PostgreSQL sin usar un volumen. Creé una tabla llamada `cuentas` y agregué un registro.

```text
PS> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

 id | saldo
----+-------
  1 |   100
(1 row)
```

Después borré el contenedor con `docker rm -f db1` y lo volví a crear. Cuando intenté consultar la tabla otra vez salió este error:

```text
PS> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

Con esto vi que, si no uso un volumen, los datos se pierden al borrar el contenedor.

Luego repetí la prueba usando el volumen `datos_banco`. Volví a borrar y crear el contenedor usando el mismo volumen y la información seguía ahí:

```text
PS> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

 id | saldo
----+-------
  1 |   100
(1 row)
```

En este caso los datos no se perdieron porque estaban guardados en el volumen.

---

## Misión 2 - Red

Con Docker Compose levantado probé si otro contenedor podía encontrar a la base de datos usando el nombre `db`.

Primero hice la prueba dentro de la red `s04_default`:

```text
PS> docker run --rm --network s04_default postgres:17-alpine pg_isready -h db

db:5432 - accepting connections
```

Después hice la misma prueba sin conectar el contenedor a esa red:

```text
PS> docker run --rm postgres:17-alpine pg_isready -h db

db:5432 - no response
```

Con esto comprobé que el nombre `db` funciona entre los contenedores que están dentro de la misma red.

---

## Misión 3 - Configuración

Usé la misma imagen `postgres:17-alpine` para crear dos contenedores con configuraciones diferentes.

En el primero usé esta configuración:

```text
PS> docker exec config_dev env | Select-String POSTGRES

POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
PGDATA=/var/lib/postgresql/data
```

Y en el segundo:

```text
PS> docker exec config_prod env | Select-String POSTGRES

POSTGRES_PASSWORD=prod-secreto
POSTGRES_DB=banco_prod
PGDATA=/var/lib/postgresql/data
```

Aquí pude ver que no necesito cambiar la imagen para usar otros valores, porque la configuración se puede pasar desde archivos externos.

También agregué `.env` y `.env.prod` al `.gitignore` para que no se suban las contraseñas al repositorio. Dejé `.env.example` como ejemplo de las variables que se necesitan.

---

## Misión 4 - Docker Compose

En `compose.yaml` levanté cuatro servicios: `web`, `db`, `cache` y `broker`.

La salida de `docker compose ps` fue:

```text
PS> docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"

NAME           SERVICE   STATUS                    PORTS
s04-broker-1   broker    Up 13 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 13 minutes             6379/tcp
s04-db-1       db        Up 13 minutes (healthy)   5432/tcp
s04-web-1      web       Up 13 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

También probé PostgreSQL y Redis desde Compose. La base `banco` apareció correctamente y Redis respondió `PONG`.

### ¿Qué se pierde con `docker compose down` y qué con `docker compose down -v`?

Con `docker compose down` se borran los contenedores y la red de Compose, pero el volumen se queda, así que los datos no se pierden.

Con `docker compose down -v` también se borra el volumen, entonces ahí sí se pierden los datos que estaban guardados.

### ¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?

Porque los dos servicios están dentro de la misma red de Docker Compose. Por eso `web` puede encontrar a `db` usando su nombre y conectarse al puerto 5432 sin tener que publicarlo hacia mi computadora.

### ¿Qué pasaría si el `.env` estuviera dentro de la imagen?

La contraseña y la configuración quedarían guardadas dentro de la imagen. Además, si quisiera cambiar algún dato tendría que volver a crear la imagen. Por eso es mejor dejar el `.env` fuera y cambiar la configuración sin modificar la imagen.
