# Evidencia de la práctica S02

Nombre: Fernando Reyes Vázquez


## Comandos utilizados

docker pull nginx:alpine
docker images

docker run -d --name web1 nginx:alpine
docker run -d --name web2 nginx:alpine
docker ps

docker stop web1
docker ps
docker ps -a
docker start web1

docker run -it --rm alpine sh
cat /etc/os-release
ps aux
exit

docker run -d -p 8080:80 --name miweb nginx:alpine
docker run -d -p 8080:80 --name choque nginx:alpine
docker rm choque

docker exec -it miweb sh
ls /usr/share/nginx/html
exit
docker logs miweb

nano index.html
nano Dockerfile

docker build -t mi-sitio .
docker images
docker run -d -p 9090:80 --name sitio mi-sitio
docker ps

nano index.html

docker build -t mi-sitio:v2 .
docker stop sitio
docker rm sitio
docker run -d -p 9090:80 --name sitio mi-sitio:v2

docker run -d -p 9091:80 --name sitio2 mi-sitio:v2
docker ps

nano evidencia.md


## docker ps

CONTAINER ID   IMAGE          COMMAND                  CREATED             STATUS             PORTS                                       NAMES
90aa040593ca   mi-sitio:v2    "/docker-entrypoint.…"   5 seconds ago       Up 4 seconds       0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
a793df74683d   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago       Up 2 minutes       0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
135f61f17011   nginx:alpine   "/docker-entrypoint.…"   47 minutes ago      Up 47 minutes      0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
feef0e868597   nginx:alpine   "/docker-entrypoint.…"   About an hour ago   Up About an hour   80/tcp                                      web2
1a36a54be1d0   nginx:alpine   "/docker-entrypoint.…"   About an hour ago   Up 58 minutes      80/tcp                                      web1


## Error de puerto ocupado

docker run -d -p 8080:80 --name choque nginx:alpine

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (0833f2aec2ef63783f4aa4a0ddd9233697c8d02e8fb60986c2ef4bbceb1cf2b3): Bind for 0.0.0.0:8080 failed: port is already allocated


## ¿Por qué no cambió la página sin reconstruir?

La página no cambió porque el archivo index.html ya había sido copiado a la imagen durante el proceso de construcción. Cuando edité el archivo en mi entorno local, la imagen y el contenedor que ya estaban construidos usaron la versión antigua. Esta fue la razón por la que se tuvo que reconstruir la imagen y crear nuevamente el contenedor.


## ¿Qué comparten y qué no dos contenedores de la misma imagen?

Los dos contenedores tienen la misma imagen base y por eso ambos empiezan con los mismos archivos y configuración. Aun así cada contenedor es una instancia propia y por eso tienen procesos separados, estados separados y puede utilizar un puerto distinto.
