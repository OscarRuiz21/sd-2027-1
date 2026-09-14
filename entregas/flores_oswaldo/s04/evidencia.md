1.- Persistencia 

sin volumen 

C:\\Users\\oswal>docker exec -it db1 psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)



C:\\Users\\oswal>docker rm -f db1

db1



C:\\Users\\oswal>docker run -d --name db1 -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

a97701ff9fc2a4f9e0a3a3b34b736037fb6dcf5c70796de0811dae6815bd527b



C:\\Users\\oswal>docker exec -it db1 psql -U postgres -c "SELECT \* FROM cuentas;"

ERROR:  relation "cuentas" does not exist

LINE 1: SELECT \* FROM cuentas;

&#x20;                     ^

C:\\Users\\oswal>

con volumen

C:\\Users\\oswal>docker rm -f db1

db1



C:\\Users\\oswal>docker run -d --name db1 -v datos\_banco:/var/lib/postgresql/data -e POSTGRES\_PASSWORD=secreto postgres:17-alpine

9cc1b7c855168ced673ddfc82c1ba764a0931e8d189978423c346985cc149e5f



C:\\Users\\oswal>docker exec -it db1 psql -U postgres -c "SELECT \* FROM cuentas;"

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)



C:\\Users\\oswal>docker volume ls

DRIVER    VOLUME NAME

local     1fa7c4aa0b4d8d6580d1dab1f12a95f5bfb1e698ff367c93c88f75f54163d45f

local     609897e10c4226eabeeeb298035ca402e746d80537459de351c3d65d77fa56d1

local     datos\_banco



C:\\Users\\oswal>docker volume inspect datos\_banco

\[

&#x20;   {

&#x20;       "CreatedAt": "2026-09-13T23:58:01Z",

&#x20;       "Driver": "local",

&#x20;       "Labels": null,

&#x20;       "Mountpoint": "/var/lib/docker/volumes/datos\_banco/\_data",

&#x20;       "Name": "datos\_banco",

&#x20;       "Options": null,

&#x20;       "Scope": "local"

&#x20;   }

]

C:\\Users\\oswal>

2.- Red

C:\Users\oswal\OneDrive\Documentos\GitHub\sd-2027-1\entregas\flores_oswaldo\s04>docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections

C:\Users\oswal\OneDrive\Documentos\GitHub\sd-2027-1\entregas\flores_oswaldo\s04>docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

3.- Configuración 

C:\Users\oswal\OneDrive\Documentos\GitHub\sd-2027-1\entregas\flores_oswaldo\s04>docker run -d --name db_dev --env-file .env postgres:17-alpine
9f1b5cc05869f97df723eaebe884b199b20697a3d697736d006608dfebe04408

C:\Users\oswal\OneDrive\Documentos\GitHub\sd-2027-1\entregas\flores_oswaldo\s04>docker exec db_dev env | findstr POSTGRES
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco

C:\Users\oswal\OneDrive\Documentos\GitHub\sd-2027-1\entregas\flores_oswaldo\s04>docker run -d --name db_prod --env-file .env.prod postgres:17-alpine

C:\Users\oswal\OneDrive\Documentos\GitHub\sd-2027-1\entregas\flores_oswaldo\s04>docker exec db_prod env | findstr POSTGRES
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod

4.- Compose

C:\Users\oswal\OneDrive\Documentos\GitHub\sd-2027-1\entregas\flores_oswaldo\s04>docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"
NAME           SERVICE   STATUS                    PORTS
s04-broker-1   broker    Up 41 seconds             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 41 seconds             6379/tcp
s04-db-1       db        Up 41 seconds (healthy)   5432/tcp
s04-web-1      web       Up 35 seconds             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp

¿Qué se pierde con down y qué con down -v?

Con down se apaga y se elimina el sistema completo, es decir, los contenedores y la red que Compose había creado, pero el volumen se conserva, por lo que los datos siguen ahí disponibles para cuando se vuelva a levantar todo con up. En cambio, con down -v ese volumen también se borra, lo que significa que toda la información guardada dentro desaparece de forma permanente y ya no hay manera de recuperarla.

¿Por qué web alcanza a db sin publicar el puerto 5432?

Web alcanza a db sin publicar el puerto 5432 porque los dos viven dentro de la misma red interna que Compose crea automáticamente. Ahí, web puede encontrar la base de datos simplemente usando su nombre, sin necesidad de que nadie exponga ese puerto hacia afuera. Publicar un puerto solo sería necesario si algo de fuera del sistema, necesitara entrar a ese servicio directamente.

¿Qué pasaría si el .env estuviera dentro de la imagen?

En primer lugar, la contraseña quedaría fija dentro de la imagen, lo que generaría que, al cambiarla, tengamos que reconstruirla por completo en vez de solo editar un archivo. Además, si esa imagen se llegara a compartir o subir a un registro público, cualquiera que la baje tendría acceso directo a la contraseña.

