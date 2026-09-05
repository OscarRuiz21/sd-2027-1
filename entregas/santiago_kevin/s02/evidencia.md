Kevin Santiago González

** Comandos utilizados **

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
docker run -d -p 8080:80 --name miweb nginx:alpine
docker build -t mi-sitio .
docker build -t mi-sitio:v2 .
docker run -d -p 9090:80 --name sitio mi-sitio:v2
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

** Salida docker ps ** 

CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
d1a1339db726   mi-sitio:v2    "/docker-entrypoint.…"   22 seconds ago   Up 21 seconds   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
283e9e9ae737   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago    Up 2 minutes    0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
a170d6f95335   nginx:alpine   "/docker-entrypoint.…"   31 minutes ago   Up 31 minutes   0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
28b22279b735   nginx:alpine   "/docker-entrypoint.…"   44 minutes ago   Up 44 minutes   80/tcp                                    web2
be4cdf802cfe   nginx:alpine   "/docker-entrypoint.…"   44 minutes ago   Up 39 minutes   80/tcp                                    web1

** Error de puerto ocupado**

b1f1a7e4af0ffa40f556aff807619848c83366849c8327d402ded4150fe770d7

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (e7eebbe86f5b935adbf98d11b7bc442569835069680d7d51832227f0f7cf8860): Bind for 0.0.0.0:9090 failed: port is already allocated

*¿Por qué no cambió la página sin recounstruir?

Porque el archivo index.html que utiliza el contenedor fue copiado dentro de la imagen cuando ejecuté docker build. Para poder aplicar cambios tenemos que construir una versión de la imagen y crear otro contenedor con dicha imagen.

*¿Qué comparten y qué no dos contenedores de la misma imagen?

Los contenedores comparten la misma imagen como inicio, por lo que tienen los mismos archivos y programas inicialmente. Pero cada contenedor tiene sus propios procesos, archivos y funcionan como una instancia independiente.
