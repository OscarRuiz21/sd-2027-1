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



¿Qué se pierde con down y qué con down -v?



Con down se apaga y se elimina el sistema completo, es decir, los contenedores y la red que Compose había creado, pero el volumen se conserva, por lo que los datos siguen ahí disponibles para cuando se vuelva a levantar todo con up. En cambio, con down -v ese volumen también se borra, lo que significa que toda la información guardada dentro desaparece de forma permanente y ya no hay manera de recuperarla.



¿Por qué web alcanza a db sin publicar el puerto 5432?



Web alcanza a db sin publicar el puerto 5432 porque los dos viven dentro de la misma red interna que Compose crea automáticamente. Ahí, web puede encontrar la base de datos simplemente usando su nombre, sin necesidad de que nadie exponga ese puerto hacia afuera. Publicar un puerto solo sería necesario si algo de fuera del sistema, necesitara entrar a ese servicio directamente.



¿Qué pasaría si el .env estuviera dentro de la imagen?



En primer lugar, la contraseña quedaría fija dentro de la imagen, lo que generaría que, al cambiarla, tengamos que reconstruirla por completo en vez de solo editar un archivo. Además, si esa imagen se llegara a compartir o subir a un registro público, cualquiera que la baje tendría acceso directo a la contraseña.

