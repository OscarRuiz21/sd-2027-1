1. Persistencia

Sin volumen

C:\Users\Jimena>docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)

C:\Users\Jimena>docker rm -f db1
db1
C:\Users\Jimena>docker run -d --name db1 -e POSTGRES_PASSWORD=secreto postgres:17-alpine
b74aa8a78336e933f763b4a08cd49b817e0a95d7b1c3fa1205a859dc33fe673c

C:\Users\Jimena>docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^

Con volumen

C:\Users\Jimena>docker rm -f db1
db1

C:\Users\Jimena>docker run -d --name db1 -v datos_banco:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
0309bc05b03ec3bbf85c7aebdcb57cea7f8ab07111d04b9fedd4ca59b09808e8

C:\Users\Jimena>docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)

C:\Users\Jimena>docker volume ls
DRIVER    VOLUME NAME
local     0be6efe1dc2b4feae984e172d4381b8e521b99d15620f56302d626f1099c81a6
local     cab7dc15b4f3f0df44ac601ea52fc22fab435083e61d73fcd90daa179e58362b
local     datos_banco
local     pgdata
local     pgtablespace

C:\Users\Jimena>docker volume inspect datos_banco
[
    {
        "CreatedAt": "2026-09-12T16:46:30Z",
        "Driver": "local",
        "Labels": null,
        "Mountpoint": "/var/lib/docker/volumes/datos_banco/_data",
        "Name": "datos_banco",
        "Options": null,
        "Scope": "local"
    }
]

2. Red

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s04>docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s04>docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

3. Configuración

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s04>docker run -d --name cfg1 --env-file .env postgres:17-alpine
389aa99cc7cb86f79d26233f5eca96c9dbfdf000fc4647e43c7a9af756270bd4

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s04>docker run -d --name cfg2 --env-file .env.prod postgres:17-alpine
eee43db04e1edc7fefaf809ef967fc742b60492ed4468a7071d1f8b04672ddf7

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s04>docker exec cfg1 env | findstr POSTGRES
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s04>docker exec cfg2 env | findstr POSTGRES
POSTGRES_PASSWORD=prod123
POSTGRES_DB=banco_prod

4. Compose

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s04>docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"
NAME           SERVICE   STATUS                   PORTS
s04-broker-1   broker    Up 3 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 3 minutes             6379/tcp
s04-db-1       db        Up 3 minutes (healthy)   5432/tcp
s04-web-1      web       Up 3 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp

¿Qué se pierde con down y qué con down -v?

Con down se apaga y se borra el sistema, pero los datos guardados en el volumen se quedan a salvo. Por otro lado con down -v se borra también ese volumen, por lo que los datos desaparecen para siempre. 

¿Por qué web alcanza a db sin publicar el puerto 5432?

Porque los dos contenedores viven en la misma red que crea Compose. Dentro de ella, Docker traduce el nombre db a su dirección interna. Publicar un puerto con "ports:" solo es necesario si se quiere que algo de fuera de esa red pueda entrar; entre contenedores que ya están adentro, no es necesario.

¿Qué pasaría si el .env estuviera dentro de la imagen?

La contraseña sería la misma siempre; si hay que modificarla, se tendría que reconstruir de cero la imagen en vez de solo editar el archivo. Y si esa imagen se comparte o se sube a algún lado público, la contraseña quedaría expuesta.