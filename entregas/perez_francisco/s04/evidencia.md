# Evidencia Laboratorio S04 - Docker día dos: estado, red, configuración y compose

## Perez Nava Francisco Javier

## 1. Persistencia

### 1.1 Sin volumen

Primero se creó una base de datos con una tabla llamada `cuentas` y se insertó un registro.

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"

CREATE TABLE

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

INSERT 0 1

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

 id | saldo
----+-------
  1 |   100
(1 row)
```

Después se eliminó el contenedor y se volvió a crear sin volumen.

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker rm -f db1

db1

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine

055c3f6b64f819c1be58cd939519f12c83cc469cf0cb5ac2c0cf1006544724ef

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

Esto demuestra que sin un volumen los datos se pierden al eliminar el contenedor.

### 1.2 Con volumen

Se volvió a crear el contenedor, ahora usando el volumen `datos_banco`.

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine

b5c079b87d61ea2593df14ee46e87ef32c283548cd1f9c01b0aa3190e21d449f

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"

CREATE TABLE

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"

INSERT 0 1
```

Después se eliminó el contenedor y se creó nuevamente usando el mismo volumen.

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker rm -f db1

db1

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine

a904857d0df16783fe84a550bbd187a5542b97d61eace08fbabb5656a00f502c

PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"

 id | saldo
----+-------
  1 |   100
(1 row)
```

En este caso la fila sí permaneció, demostrando que el volumen mantiene los datos aunque el contenedor sea eliminado.

## 2. Red

### 2.1 Conectado a la red

Se probó la conexión a la base de datos utilizando el nombre `db` desde un contenedor conectado a la red `s04_default`.

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
```

Salida:

```text
db:5432 - accepting connections
```

### 2.2 Fuera de la red

Después se realizó la misma prueba sin conectar el contenedor a la red.

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker run --rm postgres:17-alpine pg_isready -h db
```

Salida:

```text
db:5432 - no response
```

Esto demuestra que el nombre `db` puede resolverse cuando el contenedor se encuentra dentro de la misma red creada por Docker Compose.

## 3. Configuración

Se levantó la misma imagen de PostgreSQL con dos configuraciones diferentes.

### 3.1 Configuración de desarrollo

Se creó un contenedor utilizando el archivo `.env`:

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec config_dev env | Select-String POSTGRES
```

```text
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
PGDATA=/var/lib/postgresql/data
```

### 3.2 Configuración de producción

Después se creó otro contenedor utilizando el archivo `.env.prod`:

```text
PS C:\Users\franc\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\perez_francisco\s04> docker exec config_prod env | Select-String POSTGRES
```

```text
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
PGDATA=/var/lib/postgresql/data
```

Con esto se demuestra que se puede utilizar la misma imagen `postgres:17-alpine` con configuraciones diferentes, dependiendo de las variables de entorno que se le asignen.

El archivo `.env` y `.env.produ` se agregó al `.gitignore` para evitar subir las credenciales al repositorio. También se creó `.env.example` con los nombres de las variables y valores de ejemplo.

## 4. Compose

### 4.1 Servicios funcionando

La salida de `docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"` fue:

```text
NAME           SERVICE   STATUS                        PORTS
s04-broker-1   broker    Up About a minute             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up About a minute             6379/tcp
s04-db-1       db        Up About a minute (healthy)   5432/tcp
s04-web-1      web       Up About a minute             0.0.0.0:8081->80/tcp, [::]:8081->80/tcp
```

Los cuatro servicios se encuentran funcionando y la base de datos aparece como `healthy`.

### 4.2 ¿Qué se pierde con `docker compose down` y qué con `docker compose down -v`?

Con `docker compose down` se eliminan los contenedores y la red del proyecto, pero el volumen se conserva, por lo que los datos siguen almacenados.

Con `docker compose down -v` también se elimina el volumen, por lo que los datos almacenados en él se pierden.

### 4.3 ¿Por qué `web` alcanza a `db` sin publicar el puerto 5432?

Porque Docker Compose crea automáticamente una red para los servicios del proyecto. Dentro de esa red, los contenedores pueden comunicarse utilizando el nombre del servicio, en este caso `db`, sin necesidad de publicar el puerto 5432 hacia la computadora.

### 4.4 ¿Qué pasaría si el `.env` estuviera dentro de la imagen?

La configuración y las credenciales quedarían guardadas dentro de la imagen. Esto sería un problema de seguridad y además sería necesario reconstruir la imagen cada vez que se quiera cambiar la configuración.
