# Evidencia S04 - Docker

## Misión 1. Persistencia

Primero se probó un contenedor de PostgreSQL sin utilizar un volumen. Se creó la tabla `cuentas` y se insertó un registro.

```text
 id | saldo
----+-------
  1 |   100
(1 row)
```

Después se eliminó el contenedor y se creó nuevamente sin volumen. Al intentar consultar la tabla se obtuvo:

```text
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

Esto demuestra que los datos se perdieron al eliminar el contenedor cuando no había un volumen.

Después se realizó la misma prueba utilizando el volumen `datos_banco`. Al eliminar y volver a crear el contenedor utilizando el mismo volumen, la información permaneció:

```text
 id | saldo
----+-------
  1 |   100
(1 row)
```

Con esto comprobé que el volumen permite mantener los datos aunque el contenedor sea eliminado.

## Misión 2. Red

Primero se comprobó la comunicación desde otro contenedor conectado a la red `s04_default`:

```text
db:5432 - accepting connections
```

Después se realizó la prueba desde un contenedor que no estaba conectado a esa red:

```text
db:5432 - no response
```

Esto demuestra que los servicios de Docker Compose pueden comunicarse mediante el nombre del servicio cuando están dentro de la misma red.

## Misión 3. Configuración

Se utilizaron dos archivos de configuración diferentes: `.env` para desarrollo y `.env.prod` para producción.

El resultado de las variables del contenedor de desarrollo fue:

```text
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
```

Para producción se obtuvo:

```text
POSTGRES_PASSWORD=produccion123
POSTGRES_DB=banco_prod
```

Esto demuestra que se puede utilizar la misma imagen de PostgreSQL con diferentes configuraciones mediante `--env-file`.

El archivo `.env` y el archivo `.env.prod` se agregaron al `.gitignore`, mientras que `.env.example` contiene valores de ejemplo y sí puede ser incluido en el repositorio.

## Misión 4. Compose

Se creó un archivo `compose.yaml` con los servicios `web`, `db`, `cache` y `broker`. También se configuró un volumen nombrado para PostgreSQL y un `env_file` para las variables de configuración.

La salida de `docker compose ps` fue:

```text
NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    16 minutes ago   Up 16 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     16 minutes ago   Up 16 minutes             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        16 minutes ago   Up 16 minutes (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       16 minutes ago   Up 16 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

### ¿Qué se pierde con `down` vs `down -v`?

Con `docker compose down` se eliminan los contenedores y la red creada por Compose, pero los volúmenes nombrados permanecen.

Con `docker compose down -v` también se eliminan los volúmenes declarados en el archivo Compose, por lo que los datos almacenados en ellos se pueden perder.

### ¿Por qué `web` llega a `db` sin publicar el puerto 5432?

Porque ambos servicios están conectados a la misma red creada por Docker Compose. `web` puede comunicarse con el servicio `db` utilizando su nombre como hostname y el puerto interno `5432`. No es necesario publicar ese puerto hacia la computadora host.

### ¿Qué pasa si `.env` está dentro de la imagen?

Las variables y contraseñas quedarían incorporadas dentro de la imagen o de sus capas. Esto puede provocar que los datos de configuración queden expuestos a personas que tengan acceso a la imagen y además dificulta cambiar la configuración entre ambientes.

Por eso es mejor utilizar variables de entorno o archivos de configuración externos y no incluir secretos dentro de la imagen.
