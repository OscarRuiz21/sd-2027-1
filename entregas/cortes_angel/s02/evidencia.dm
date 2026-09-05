# Evidencia Práctica Docker

## Comandos utilizados en la Misión 2 y 3
* `docker build -t mi-sitio:v2 .`
* `docker rm -f sitio`
* `docker run -d -p 9090:80 --name sitio mi-sitio:v2`
* `docker run -d -p 9091:80 --name sitio2 mi-sitio:v2`

## Salida de docker ps
$ docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED              STATUS              PORTS                                     NAMES
a7ccc94f0889   mi-sitio:v2    "/docker-entrypoint.…"   36 seconds ago       Up 35 seconds       0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
6bc420655352   mi-sitio:v2    "/docker-entrypoint.…"   About a minute ago   Up About a minute   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
b17f0e39e0cc   nginx:alpine   "/docker-entrypoint.…"   32 minutes ago       Up 32 minutes       0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb


## Error de puerto ocupado
$ docker run -d -p 8080:80 --name choque nginx:alpine
c9ef9c0f0b183bbf62892aac858b56cda448994b31f138d45ec3ebdb4615f2e4

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (05810680efd529ab7315aec3089a620da02ceb939896f26040f938d9d9391ff2): Bind for 0.0.0.0:8080 failed: port is already allocated


**¿Por qué no cambió la página sin reconstruir?**
Porque las imágenes de Docker son inmutables. 
Al hacer el *build*, Docker tomó una fotografía de mis archivos y la congeló. 
Modificar el archivo `index.html` en mi disco duro no altera el interior del 
contenedor que ya está construido y corriendo.

**¿Qué comparten y qué no dos contenedores de la misma imagen?**
Comparten exactamente los mismos archivos base, configuraciones y dependencias (la imagen de lectura). 
No comparten los puertos expuestos hacia mi computadora, el espacio en memoria, ni los archivos nuevos que se generen o modifiquen internamente mientras están encendidos.
