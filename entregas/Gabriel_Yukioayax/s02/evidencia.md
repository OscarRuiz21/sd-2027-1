# Evidencia de la Práctica 1 - Docker

**Nombre:** Yukioayax

## Comandos que usé hoy:
- docker pull nginx:alpine
- docker images
- docker run -d --name web1 nginx:alpine
- docker stop / start / rm
- docker ps / docker ps -a
- docker run -it --rm alpine sh
- docker run -d -p 8080:80 --name miweb nginx:alpine
- docker exec -it miweb sh
- docker logs miweb
- docker build -t mi-sitio .
- docker build -t mi-sitio:v2 .

## El error de puerto ocupado:
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (...): Bind for 0.0.0.0:8080 failed: port is already allocated

## Mis contenedores multiplicados (docker ps):
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
1282322869a8   mi-sitio:v2    "/docker-entrypoint.ÔÇª"   6 minutes ago    Up 6 minutes    0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
eb0de204a906   mi-sitio:v2    "/docker-entrypoint.ÔÇª"   6 minutes ago    Up 6 minutes    0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
edaf11fdb767   nginx:alpine   "/docker-entrypoint.ÔÇª"   31 minutes ago   Up 31 minutes   0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
2048d05e813c   nginx:alpine   "/docker-entrypoint.ÔÇª"   37 minutes ago   Up 37 minutes   80/tcp                                    web2
42f40e3b01da   nginx:alpine   "/docker-entrypoint.ÔÇª"   37 minutes ago   Up 34 minutes   80/tcp                                    web1

## Respuestas:

**¿Por qué no cambió la página sin reconstruir? (La trampa)**
Porque las imágenes de Docker son inmutables (de solo lectura), el contenedor 'sitio' se generó con la versión original del index.html; aunque el archivo se modificó en el sistema host, estos cambios no se reflejan automáticamente en el contenedor aislado, fue necesario realizar un nuevo build para empaquetar la capa 'v2' con el archivo actualizado.

**¿Qué comparten y qué no dos contenedores de la misma imagen?**
Comparten la misma imagen base de solo lectura ('mi-sitio:v2') y la estructura de archivos de la aplicación; no comparten su memoria, sus procesos, su capa temporal de lectura/escritura (cada contenedor tiene una capa independiente) ni el puerto del host al que están expuestos (uno utiliza el 9090 y el otro el 9091).
