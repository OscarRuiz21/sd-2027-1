# Evidencia S04 — Docker día dos

## Misión 1 · Persistencia

### Sin volumen (falla)

```
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)


What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker rm -f db1
db1
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
c7c29e601411e4be2e2f24928467168d20baf7215d99304744ceb4919b4386ed
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
```

### Con volumen (sobrevive)

```
>> docker rm -f db1
db1
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
99e7b4f6bd7bb65d446cc1718acfeab73d86bfe46d210f2143efb465de8a1455
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: No such file or directory
        Is the server running locally and accepting connections on that socket?

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker exec -it db1 psql -U postgres -c "CREATE TABLE cuentas(id int primary key, saldo int);"
CREATE TABLE

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker exec -it db1 psql -U postgres -c "INSERT INTO cuentas VALUES (1, 100);"
INSERT 0 1

What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker rm -f db1
db1
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
>> docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
7512ff1c67c8e7016f99805544a10404a934212d3db1997d800c5535a0872f39
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)


What's next:
    Try Docker Debug for seamless, persistent debugging tools in any container or image → docker debug db1
    Learn more at https://docs.docker.com/go/debug-cli/
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
```

## Misión 2 · Red

```
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04>
```

## Misión 3 · Configuración

```
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker exec db_dev env | sls POSTGRES

POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev
PGDATA=/var/lib/postgresql/data


PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker exec db_prod env | sls POSTGRES

POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
PGDATA=/var/lib/postgresql/data


PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker run --rm --env-file .env postgres:17-alpine env | sls POSTGRES

POSTGRES_PASSWORD=secreto
PGDATA=/var/lib/postgresql/data
POSTGRES_DB=banco

```

## Misión 4 · Compose

```
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s04> docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"
NAME           SERVICE   STATUS                        PORTS
s04-broker-1   broker    Up About a minute             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up About a minute             6379/tcp
s04-db-1       db        Up About a minute (healthy)   5432/tcp
s04-web-1      web       Up About a minute             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

### Preguntas

**¿qué se pierde con down y qué con down -v?**

Con down se borran los contenedores y la red, pero el volumen s04_datos_banco se conserva (lo comprobé con docker volume ls, seguía apareciendo después del down). Con down -v el volumen también se borra, y con él se pierden los datos guardados dentro (después de correrlo s04_datos_banco ya no aparece en docker volume ls).

**¿Por qué web alcanza a db sin publicar el puerto 5432?**

Porque Compose crea automáticamente una red y conecta todos los servicios ahí. Dentro de esa red, cada servicio se resuelve por su nombre db, sin necesidad de publicar el puerto hacia mi máquina , solo hace falta publicar los puertos que quiero usar.

**¿Qué pasaría si el .env estuviera dentro de la imagen?**

La contraseña y la configuración quedarían fijas dentro de la imagen, así que para cambiarlas entre desarrollo y producción habría que reconstruir la imagen cada vez. Además, cualquiera con acceso a la imagen podría ver la contraseña y eso es un riesgo de seguridad.