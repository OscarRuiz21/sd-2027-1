# Evidencia de la Sesión 04

## Misión 1: Persistencia

### Prueba 1: Sin volumen (Pérdida de datos)
 Al eliminar el contenedor sin un volumen asociado, los datos insertados en la tabla se pierden al recrearlo.

```text
ERROR:  relation "prueba" does not exist
LINE 1: SELECT * FROM prueba;

Prueba 2: Con volumen (Persistencia de datos)
Al usar un volumen nombrado, los datos sobreviven a la eliminación del contenedor (docker rm -f).


 id 
----
  1
(1 row)


Misión 2: Redes
Comprobación de resolución DNS de Docker dentro y fuera de la red del proyecto:

PS C:\Users\Diego Alexander\sd-2027-1\entregas\martinez_diego\s04> docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections

PS C:\Users\Diego Alexander\sd-2027-1\entregas\martinez_diego\s04> docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

Misión 3: Configuración
Misma imagen (postgres:17-alpine) ejecutada con distintos archivos o variables de entorno:

PS C:\Users\Diego Alexander\sd-2027-1\entregas\martinez_diego\s04> docker exec app_dev env | Select-String "POSTGRES"

POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
PGDATA=/var/lib/postgresql/data

PS C:\Users\Diego Alexander\sd-2027-1\entregas\martinez_diego\s04> docker exec app_prod env | Select-String "POSTGRES"

POSTGRES_USER=prod_user
POSTGRES_PASSWORD=secret_pass
POSTGRES_DB=prod_db
PGDATA=/var/lib/postgresql/data


Misión 4: Compose
Estado de los servicios (docker compose ps)
Plaintext
PS C:\Users\Diego Alexander\sd-2027-1\entregas\martinez_diego\s04> docker compose up -d
[+] up 4/4
 ✔ Container s04-broker-1 Running                                       0.0s
 ✔ Container s04-web-1    Running                                       0.0s
 ✔ Container s04-db-1     Healthy                                       0.6s
 ✔ Container s04-cache-1  Running                                       0.0s

NAME           IMAGE                        COMMAND                  SERVICE   CREATED        STATUS                  PORTS
s04-broker-1   rabbitmq:4-management-alpine "docker-entrypoint.s…"   broker    20 minutes ago Up 20 minutes          0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     20 minutes ago Up 20 minutes          6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        20 minutes ago Up 20 minutes (healthy)  5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       20 minutes ago Up 20 minutes          0.0.0.0:8080->80/tcp, [::]:8080->80/tcp


Respuestas a las preguntas teóricas

¿Qué se pierde con down y qué con down -v?

docker compose down: Elimina los contenedores, las redes creadas y los volúmenes anónimos, pero mantiene intactos los volúmenes nombrados (los datos de la base de datos no se pierden).

docker compose down -v: Elimina los contenedores, las redes y también borra los volúmenes nombrados asociados en la configuración, destruyendo la persistencia de los datos.

¿Por qué web alcanza a db sin publicar el puerto 5432?

Porque Docker Compose crea una red interna (s04_default) donde todos los contenedores pertenecientes a esa red se comunican directamente a través de sus nombres de servicio y puertos internos. Publicar el puerto con ports: (ej. 5432:5432) solo es necesario para exponer la base de datos hacia la máquina host (tu computadora externa), no para la comunicación interna entre contenedores.

¿Qué pasaría si el .env estuviera dentro de la imagen?

Se violaría el principio de separación de configuración y código (Factor III de las 12-Factor Apps). La imagen perdería su portabilidad/reutilización para distintos entornos (dev, staging, prod) y, más grave aún, se expondrían datos sensibles (contraseñas, API keys) en repositorios o registries públicos a donde se suba dicha imagen.

