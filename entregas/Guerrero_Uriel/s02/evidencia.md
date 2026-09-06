Evidencia S02 - Docker
Misión 1 - Haz tuya la página

Modifiqué mi archivo index.html para incluir mi nombre y una tabla con los cinco comandos de Docker que más me sirvieron durante la práctica.

Mis 5 comandos más útiles
docker run: Lo utilicé para crear y poner a funcionar un contenedor a partir de una imagen.
docker ps: Me permitió revisar cuáles de mis contenedores estaban funcionando.
docker stop: Sirve para detener un contenedor sin eliminarlo.
docker exec -it nombre sh: Me permitió entrar al interior de un contenedor que ya estaba funcionando y revisar sus archivos.
docker build -t mi-sitio .: Lo utilicé para construir mi propia imagen utilizando el Dockerfile y el archivo index.html.
Misión 2 - La trampa

Primero construí la imagen mi-sitio y ejecuté el contenedor sitio utilizando el puerto 9090.

Después modifiqué el archivo index.html agregando una línea nueva y recargué la página en http://localhost:9090.

La nueva línea no apareció inmediatamente.

¿Por qué no cambió la página sin reconstruir?

No cambió porque el contenedor estaba utilizando la imagen que había sido construida anteriormente. La imagen contiene la versión de index.html que existía en el momento en que se realizó el docker build.

Modificar el archivo original en mi computadora no modifica automáticamente la imagen que ya fue construida. Por eso fue necesario volver a construir la imagen para incluir los cambios.

Después construí la nueva versión con:

docker build -t mi-sitio:v2 .

Eliminé el contenedor anterior y levanté uno nuevo utilizando mi-sitio:v2. Después de esto, el cambio sí apareció en localhost:9090.

Misión 3 - Multiplícate

Utilicé la misma imagen mi-sitio:v2 para ejecutar dos contenedores al mismo tiempo.

El contenedor sitio utiliza el puerto 9090 y el contenedor sitio2 utiliza el puerto 9091.

Salida de docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED              STATUS              PORTS                                     NAMES
a9fa1a5f397e   mi-sitio:v2    "/docker-entrypoint.…"   About a minute ago   Up About a minute   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
86a1b0d39ac7   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago        Up 2 minutes        0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
a55b295e9867   nginx:alpine   "/docker-entrypoint.…"   39 minutes ago       Up 39 minutes       0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
0577e098bdd7   nginx:alpine   "/docker-entrypoint.…"   47 minutes ago       Up 47 minutes       80/tcp                                    web2
eaa85c6922ef   nginx:alpine   "/docker-entrypoint.…"   47 minutes ago       Up 43 minutes       80/tcp                                    web1

Esto demuestra que una misma imagen puede utilizarse para crear varias instancias de contenedores. En este caso, sitio y sitio2 utilizan mi-sitio:v2, pero son contenedores independientes y utilizan diferentes puertos.

Misión 4 - Evidencia escrita
Comandos utilizados

Durante la práctica utilicé diferentes comandos de Docker para descargar imágenes, crear contenedores, detenerlos, iniciarlos, inspeccionarlos, consultar sus logs y construir mi propia imagen.

docker pull nginx:alpine
docker images
docker run -d --name web1 nginx:alpine
docker run -d --name web2 nginx:alpine
docker ps
docker stop web1
docker ps -a
docker start web1
docker run -it --rm alpine sh
docker exec -it miweb sh
docker logs miweb
docker build -t mi-sitio .
docker build -t mi-sitio:v2 .
docker rm -f sitio
docker run -d -p 9090:80 --name sitio mi-sitio:v2
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2
Docker ps

La siguiente fue la salida obtenida cuando tuve funcionando los dos contenedores de mi imagen:

CONTAINER ID   IMAGE          COMMAND                  CREATED              STATUS              PORTS                                     NAMES
a9fa1a5f397e   mi-sitio:v2    "/docker-entrypoint.…"   About a minute ago   Up About a minute   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
86a1b0d39ac7   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago        Up 2 minutes        0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
a55b295e9867   nginx:alpine   "/docker-entrypoint.…"   39 minutes ago       Up 39 minutes       0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
0577e098bdd7   nginx:alpine   "/docker-entrypoint.…"   47 minutes ago       Up 47 minutes       80/tcp                                    web2
eaa85c6922ef   nginx:alpine   "/docker-entrypoint.…"   47 minutes ago       Up 43 minutes       80/tcp                                    web1
Error de puerto ocupado

Para producir el error solicitado en la práctica, intenté crear otro contenedor utilizando nuevamente el puerto 9090:

docker run -d -p 9090:80 --name choque mi-sitio:v2

El resultado fue:

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (b142f9896dbe9195cc41297779a35911aa062d2220ef3c06888075aa43136e74): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information

El error ocurrió porque el puerto 9090 ya estaba siendo utilizado por el contenedor sitio. Por esta razón Docker no pudo asignar el mismo puerto a otro contenedor.

¿Por qué no cambió la página sin reconstruir?

La página no cambió porque el contenedor estaba utilizando una imagen que había sido construida antes de modificar el archivo index.html. Los cambios realizados posteriormente en el archivo original no modifican automáticamente la imagen existente.

Para que el cambio apareciera fue necesario construir una nueva versión de la imagen con docker build y después crear un nuevo contenedor utilizando esa imagen.

¿Qué comparten y qué no dos contenedores de la misma imagen?

Dos contenedores de la misma imagen comparten la misma imagen base y los archivos que fueron incluidos dentro de ella durante el proceso de construcción.

Sin embargo, los contenedores son instancias independientes. Cada uno tiene sus propios procesos, estado y configuración de ejecución.

En mi práctica, sitio y sitio2 utilizan la misma imagen mi-sitio:v2, pero son contenedores diferentes y están publicados en puertos diferentes: 9090 y 9091.
