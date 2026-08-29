
# Evidencia · Sesion 02

## 1. Comandos ejecutados en el reto
```bash
# Mision 1: Construccion y arranque inicial
docker build -t mi-sitio .
docker run -d -p 9090:80 --name sitio mi-sitio

# Mision 2: Actualizacion a v2 tras verificar inmutabilidad
docker build -t mi-sitio:v2 .
docker stop sitio
docker rm sitio
docker run -d -p 9090:80 --name sitio mi-sitio:v2

# Mision 3: Multiplicar contenedores en puertos distintos
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

APARTADO DE TABLA:


CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
e36cf3f2efb2   mi-sitio:v2    "/docker-entrypoint.…"   18 seconds ago   Up 17 seconds   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
6c18a30d2a4b   mi-sitio:v2    "/docker-entrypoint.…"   3 minutes ago    Up 3 minutes    0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
0cfbe8e15023   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours      0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
8411aa1c14c8   nginx:alpine   "/docker-entrypoint.…"   3 hours ago      Up 3 hours      80/tcp                                    web2
81b7e8e95c19   nginx:alpine   "/docker-entrypoint.…"   3 hours ago      Up 3 hours      80/tcp                                    web1
# APARTADO DE ERROR:
cecis@Asus_Ceci MINGW64 ~/sd-2027-1/entregas/solis_cecilia/s02 (s02-solis)
$ docker run -d -p 9090:80 --name choque mi-sitio:v2
7dc55996f9bfa854ae2cb9c1d85a91edd90edf9fade272c1ab1f9904469f5a1c

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (f3159a6cec2606174b642150912da5e7aa4a0a054c4ef9683681f78a2f408651): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information

# preguntas
# POR QUE NO CAMBIO LA PAGINA SIN RECONSTRUIR: 
# las imagenes de docker son inmutables, se modifico el archivo index de manera local, pero el contenedor aun sigue leyendo la copia congelada hasta que se compile una nuevaimagen y se levante un nuevo contenedor.

# QUE COMPARTEN Y QUE NO DOS CONTENEDORES DE LA MISMA IMAGEN:
# el sistema alpine linux, kernel del host y archivos binarios de Nginx; sin embargo, no comparten direccion ip virtual interna, puerto mapeado en maquina host, tienen sistema de archivos independientes en momento de ejecucion.