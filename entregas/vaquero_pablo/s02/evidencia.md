# Evidencia del laboratorio S02 — Docker

**Nombre:** Pablo Vaquero  
**Rama:** `s02-vaquero`

## Comandos utilizados

```text
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
docker exec -it miweb sh
ls /usr/share/nginx/html
exit
docker logs miweb
docker build -t mi-sitio .
docker run -d -p 9090:80 --name sitio mi-sitio
docker build -t mi-sitio:v2 .
docker stop sitio
docker rm sitio
docker run -d -p 9090:80 --name sitio mi-sitio:v2
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2
docker ps
```

## Contenedores ejecutándose

```text
CONTAINER ID   IMAGE          STATUS         PORTS                                     NAMES
9b7b27c2d653   mi-sitio:v2    Up             0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
c093d7b8f3e0   mi-sitio:v2    Up             0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
fd9255947654   nginx:alpine   Up             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
dfaa0438b10c   nginx:alpine   Up             80/tcp                                    web2
bd50f2227ad9   nginx:alpine   Up             80/tcp                                    web1
```

## Error de puerto ocupado

Intenté iniciar otro contenedor usando el puerto 8080, que ya estaba ocupado por `miweb`.

```text
docker run -d -p 8080:80 --name choque nginx:alpine

docker: Error response from daemon: failed to set up container networking:
driver failed programming external connectivity on endpoint choque:
Bind for 0.0.0.0:8080 failed: port is already allocated
```

El error ocurrió porque dos contenedores no pueden publicar simultáneamente el mismo puerto de la computadora.

## ¿Por qué no cambió la página sin reconstruir?

No cambió porque, durante la construcción, Docker copió el archivo `index.html` dentro de la imagen. Modificar después el archivo de mi carpeta no cambia la copia que ya quedó guardada en esa imagen ni el contenedor que estaba ejecutándose. Para mostrar la modificación tuve que construir `mi-sitio:v2`, eliminar el contenedor anterior y crear uno nuevo con esa imagen.

## ¿Qué comparten y qué no comparten dos contenedores de la misma imagen?

Los dos contenedores comparten la misma imagen y, por lo tanto, parten de los mismos archivos, nginx y configuración inicial. Sin embargo, son instancias independientes: cada uno tiene su propio identificador, nombre, procesos, estado, capa de escritura y conexión de red. También necesitan puertos diferentes en la computadora, aunque ambos utilicen internamente el puerto 80.
