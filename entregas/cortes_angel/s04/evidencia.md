####Cortés Angel####

####Práctica 2#####


#####Aquí se muestra la diferencia entre no usar volúmenes (los datos se pierden al borrar el contenedor) y sí usarlos (la tabla y sus datos sobreviven).####

angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/s04 (entregas_cortes_angel)
$ docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^


angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/s04 (entregas_cortes_angel)
$ docker exec -it db1 psql -U postgres -c "SELECT * FROM cuentas;"
 id | saldo
----+-------
  1 |   100
(1 row)



#########En esta prueba se comprueba que el nombre del contenedor sirve como dirección para encontrarse entre sí, pero esto solo funciona cuando ambos contenedores están conectados a la misma red interna.#######

angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/s04 (entregas_cortes_angel)
$ docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
db:5432 - accepting connections


angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/s04 (entregas_cortes_angel)
$ docker run --rm postgres:17-alpine pg_isready -h db
db:5432 - no response

########Se demuestra que una misma imagen base puede tener configuraciones completamente distintas (como contraseñas o nombres de bases de datos) inyectando diferentes archivos externos, separando así la configuración del código.#######

angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/s04 (entregas_cortes_angel)
$ docker exec prueba_dev env | grep POSTGRES
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco

angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/s04 (entregas_cortes_angel)
$ docker exec prueba_prod env | grep POSTGRES
POSTGRES_PASSWORD=super_secreto
POSTGRES_DB=banco_produccion



##########Evidencia del sistema completo levantado con Docker Compose en un solo comando, con los cuatro servicios conectados y la base de datos reportando un estado saludable.########

angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/s04 (entregas_cortes_angel)
$ docker compose ps
NAME           IMAGE                          COMMAND                  SERVICE   CREATED          STATUS                    PORTS
s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    18 minutes ago   Up 18 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     18 minutes ago   Up 18 minutes             6379/tcp
s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        18 minutes ago   Up 18 minutes (healthy)   5432/tcp
s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       18 minutes ago   Up 18 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp

#####Preguntas#####

¿Qué se pierde con down y qué con down -v?
Con down se borran los contenedores y la red del proyecto, pero se conserva el volumen y los datos. Con down -v se borra todo, incluyendo el volumen, por lo que los datos se pierden definitivamente.

¿Por qué web alcanza a db sin publicar el puerto 5432?
Porque Compose conecta automáticamente a todos los servicios en una misma red interna (por ejemplo, s04_default). Dentro de esta red, los contenedores se encuentran por su nombre (DNS de Docker), sin necesidad de publicar los puertos hacia la máquina principal.

¿Qué pasaría si el .env estuviera dentro de la imagen?
La imagen perdería flexibilidad, ya que no serviría para dos entornos distintos (desarrollo y producción) sin tener que ser reconstruida. Además, estaríamos exponiendo contraseñas y datos sensibles si compartimos la imagen o el repositorio, violando la regla de separar la configuración del código.