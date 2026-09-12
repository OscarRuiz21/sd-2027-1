\# Evidencia S02 - Docker



Nombre: Bali Yandrei



1. &#x20;Comandos que use

docker pull nginx:alpine

docker images

docker run -d --name web1 nginx:alpine

docker run -d --name web2 nginx:alpine

docker ps

docker stop web1

docker ps -a

docker start web1

docker run -it --rm alpine sh

cat /etc/os-release

ps aux

exit

docker run -d -p 8080:80 --name miweb nginx:alpine

docker ps

docker exec -it miweb sh

ls /usr/share/nginx/html

exit

docker logs miweb

docker build -t mi-sitio .

docker images

docker run -d -p 9090:80 --name sitio mi-sitio

docker build -t mi-sitio:v2 .

docker stop sitio

docker rm sitio

docker run -d -p 9090:80 --name sitio mi-sitio:v2

docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

docker ps





2\. Resultado de docker ps

CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS             PORTS                                     NAMES

1a7e71f080e6   mi-sitio:v2    "/docker-entrypoint.…"   19 minutes ago   Up 19 minutes      0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

c3399c1a5b18   mi-sitio:v2    "/docker-entrypoint.…"   21 minutes ago   Up 20 minutes      0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio

082bcff066fe   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up About an hour   0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp   miweb

ec1d19ca9d46   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours         80/tcp                                    web2

2d45f39cf239   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours         80/tcp                                    web1



3\. Error de puerto ocupado

3527ec81511f1d2441d8cabcf7af76bcbc17b8c28678739cb75fe7dcd488b6d5



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint prueba-puerto (918ebb0626c5a31efe7c1c2c9e1a8ef175260afd5293d296f82bba23361a7487): Bind for 0.0.0.0:9090 failed: port is already allocated

4\. ¿Por que no cambio la pagina sin reconstruir?

La pagina no cambio porque el contenedor estaba usando la imagen que ya habia construido antes. Aunque modifique el archivo index.html en mi computadora y ese cambio no se actualiza automaticamente dentro de la imagen y tuve que volver a construir la imagen y crear de nuevo el contenedor para poder ver los cambios.

5\. ¿Que comparten y que no dos contenedores de la misma imagen?

Los dos contenedores usan la misma imagen como base y por eso tienen los mismos archivos y la misma configuracion inicial. Pero cada contenedor funciona de manera independiente y tiene sus propios procesos y puede usar un puerto diferente.

