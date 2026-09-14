# Evidencia S04 · Docker: volúmenes, redes, variables de entorno y Compose

**Estudiante:** Victoria Muñoz  
**Carpeta:** `entregas/munoz_victoria/s04`  
**Rama:** `entregas_munoz_victoria`

---

## Misión 1 · Persistencia con volúmenes

### Sin volumen

Se creó un contenedor PostgreSQL sin volumen:

```bash
docker run -d --name db1 -e POSTGRES_PASSWORD=pepocumbia7 postgres:17-alpine
```

Se creó la tabla, se insertó una fila y se consultó:

```bash
winpty docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
winpty docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
winpty docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
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
docker run -d --name db1 -e POSTGRES_PASSWORD=pepocumbia7 postgres:17-alpine
winpty docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
```

Resultado:

```text
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
```

**Conclusión:** sin un volumen, los datos almacenados en la capa de escritura del contenedor se pierden al eliminarlo.

### Con volumen

Se creó el contenedor usando un volumen nombrado:

```bash
docker rm -f db1
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
```

Se creó la tabla y se insertó una fila:

```bash
winpty docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
winpty docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
```

Después se eliminó y recreó el contenedor usando el mismo volumen:

```bash
docker rm -f db1
docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
winpty docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
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
docker volume ls | grep datos_banco
```

Salida:

```text
local    datos_banco
local    s04_datos_banco
```

**Conclusión:** el contenedor se eliminó, pero el volumen permaneció y permitió recuperar los datos.

---

## Misión 2 · Red y descubrimiento por nombre

Se creó una red definida por el usuario:

```bash
docker network create redlab
```

Se levantó nginx dentro de esa red:

```bash
docker run -d --name web --network redlab nginx:alpine
```

Desde otro contenedor conectado a la misma red se consultó nginx usando su nombre:

```bash
docker run --rm --network redlab alpine:3.20 wget -qO- http://web
```

La respuesta fue el HTML de la página de bienvenida de nginx:

```text
<!DOCTYPE html>
<html>
<head>
<title>Welcome to nginx!</title>
...
<h1>Welcome to nginx!</h1>
<p>If you see this message, nginx is successfully installed and working.
```

Se comprobó también que fuera de la red el nombre `web` no se puede resolver:

```bash
docker run --rm alpine:3.20 wget -qO- http://web
```

Resultado:

```text
wget: bad address 'web'
```

**Conclusión:** dentro de una red definida por Docker, los contenedores pueden encontrarse mediante el nombre del contenedor. No es necesario conocer ni usar directamente su dirección IP. Fuera de esa red, el nombre `web` no identifica al contenedor.

---

## Misión 3 · Configuración mediante variables de entorno

Se levantaron dos contenedores usando la misma imagen `postgres:17-alpine`, pero con configuraciones diferentes:

```bash
docker run -d --name db_dev --network redlab -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=banco_dev postgres:17-alpine

docker run -d --name db_prod --network redlab -e POSTGRES_PASSWORD=prod -e POSTGRES_DB=banco_prod postgres:17-alpine
```

Se verificaron las variables recibidas por cada contenedor:

```bash
docker exec db_dev env | grep POSTGRES
docker exec db_prod env | grep POSTGRES
```

Salida:

```text
POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
POSTGRES_DB=banco_prod
POSTGRES_PASSWORD=prod
```

La misma imagen fue utilizada en ambos casos, pero cada contenedor recibió una configuración diferente.

También se utilizó un archivo `.env` con:

```text
POSTGRES_PASSWORD=pepocumbia7
POSTGRES_DB=banco
```

Y posteriormente Compose resolvió esas variables correctamente.

**Conclusión:** la imagen permanece igual y la configuración puede cambiar mediante variables de entorno o archivos `.env`. Esto permite utilizar la misma imagen para diferentes ambientes sin reconstruirla.

### Protección del archivo `.env`

El archivo `.env` contiene una contraseña y no debe subirse al repositorio.

El `.gitignore` debe contener:

```text
.env
```

El archivo `.env.example` puede contener valores falsos:

```text
POSTGRES_PASSWORD=cambia-esto
POSTGRES_DB=banco
```

---

## Misión 4 · Docker Compose

Se creó un `compose.yaml` con cuatro servicios:

- `web` → nginx
- `db` → PostgreSQL
- `cache` → Redis
- `broker` → RabbitMQ

Configuración utilizada:

```yaml
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
    volumes:
      - datos_banco:/var/lib/postgresql/data
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

volumes:
  datos_banco:
```

Se levantó el sistema con:

```bash
docker compose up -d
```

Resultado:

```text
[+] up 5/5
 ✔ Network s04_default       Created
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
s04-broker-1   broker    Up 9 seconds             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 9 seconds             6379/tcp
s04-db-1       db        Up 9 seconds (healthy)   5432/tcp
s04-web-1      web       Up 3 seconds             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

Se verificó la configuración resuelta:

```bash
docker compose config
```

Compose mostró, entre otros datos:

```text
POSTGRES_DB: banco
POSTGRES_PASSWORD: pepocumbia7
image: postgres:17-alpine
source: datos_banco
target: /var/lib/postgresql/data
name: s04_default
name: s04_datos_banco
```

Se comprobó que PostgreSQL tiene la base `banco`:

```bash
docker compose exec db psql -U postgres -c "\l"
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

---

## Preguntas de la Misión 4

### 1. ¿Qué se pierde con `docker compose down` y qué con `docker compose down -v`?

`docker compose down` elimina los contenedores y la red del proyecto, pero conserva los volúmenes. Por lo tanto, los datos de PostgreSQL permanecen.

`docker compose down -v` también elimina los volúmenes del proyecto. Al eliminar el volumen `s04_datos_banco`, se pierden los datos almacenados en él.

### 2. ¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?

Porque Compose crea automáticamente una red para el proyecto y conecta todos los servicios a esa red. Dentro de esa red, los servicios se pueden encontrar mediante su nombre de servicio.

Por eso `web` puede comunicarse con `db:5432` aunque el puerto 5432 de PostgreSQL no esté publicado en la máquina host.

### 3. ¿Qué pasaría si el `.env` estuviera dentro de la imagen?

La configuración y las credenciales quedarían incorporadas a la imagen. Esto sería inseguro, porque cualquiera que tenga acceso a la imagen podría obtener información que debería mantenerse fuera de ella.

La configuración debe mantenerse fuera de la imagen y proporcionarse mediante variables de entorno o archivos de configuración externos. El archivo `.env` tampoco debe subirse al repositorio.

---

## Comprobaciones adicionales

Se comprobó la configuración final con:

```bash
docker compose config
```

Se comprobó la base de datos:

```bash
docker compose exec db psql -U postgres -c "\l"
```

Se comprobó Redis:

```bash
docker compose exec cache redis-cli ping
```

Resultado:

```text
PONG
```

---

## Archivos que deben entregarse

La carpeta `s04` debe contener:

```text
compose.yaml
.env.example
.gitignore
evidencia.md
```

El archivo `.env` **NO debe subirse** al repositorio porque contiene la contraseña.

---

## Entrega

Desde la raíz del repositorio:

```bash
git status
git add entregas/munoz_victoria/s04
git commit -m "S04: volúmenes, redes, env y compose"
git push
```

Si es el primer push de la rama:

```bash
git push -u origin entregas_munoz_victoria
```

La entrega se realiza mediante el push a la rama `entregas_munoz_victoria`.

