# Evidencia S02 · Docker, día uno
## Melissa Saucedo

## Comandos que usé

docker pull nginx:alpine
docker images
docker run -d --name web1 nginx:alpine
docker run -d --name web2 nginx:alpine
docker ps
docker stop web1
docker ps -a
docker start web1
docker run -it --rm alpine sh
docker run -d -p 8080:80 --name miweb nginx:alpine
docker run -d -p 8080:80 --name choque nginx:alpine
docker rm choque
docker exec -it miweb sh
docker logs miweb
docker build -t mi-sitio .
docker run -d -p 9090:80 --name sitio mi-sitio
docker build -t mi-sitio:v2 .
docker stop sitio
docker rm sitio
docker run -d -p 9090:80 --name sitio mi-sitio:v2
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

## docker ps con mis dos contenedores (sitio y sitio2)

CONTAINER ID   IMAGE          COMMAND                  CREATED              STATUS              PORTS                                     NAMES
ad414567e2f4   mi-sitio:v2    "/docker-entrypoint...."   5 seconds ago        Up 5 seconds        0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
cabfb506a809   mi-sitio:v2    "/docker-entrypoint...."   About a minute ago   Up About a minute   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
224b8e67723b   nginx:alpine   "/docker-entrypoint...."   27 minutes ago       Up 27 minutes       0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
ceee9d3fe148   nginx:alpine   "/docker-entrypoint...."   37 minutes ago       Up 37 minutes       80/tcp                                    web2
139ebc94af65   nginx:alpine   "/docker-entrypoint...."   38 minutes ago       Up 32 minutes       80/tcp                                    web1

## Error de puerto ocupado (al intentar levantar "choque" en el puerto 8080)

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (f12593d3a802261395c3ecbd3a410d3eb894a7412d0c4a6c9576ce951bd5bddf): Bind for 0.0.0.0:8080 failed: port is already allocated

## Preguntas

**¿Por qué no cambió la página sin reconstruir?**
Sucedió porque aunque ya había editado el index.html, el contenedor (sitio) todavía estaba usando la copia que se había guardado en el primer build. La imagen quedó congelada con esa versión del archivo, y el contenedor no vuelve a leer mi carpeta después de construida, por eso tuve que reconstruir la imagen para que el cambio se reflejara.

**¿Qué comparten y qué no comparten dos contenedores de la misma imagen?**
Comparten la misma imagen base (mi-sitio:v2): el mismo index.html, el mismo nginx y la misma configuración, porque vienen del mismo Dockerfile. Pero no comparten el puerto, el nombre ni el CONTAINER ID, cada uno corre por separado. Si uno se cae, el otro sigue vivo, igual que pasó con web1 y web2.
