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


CONTAINER ID   IMAGE         COMMAND                  CREATED          STATUS          PORTS                                     NAMES
7fec90d0b836   mi-sitio:v2   "/docker-entrypoint.…"   6 minutes ago    Up 6 minutes    0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
0ce6f2694ed6   mi-sitio:v2   "/docker-entrypoint.…"   6 minutes ago    Up 6 minutes    0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio

# APARTADO DE ERROR:
luisa@LAPTOP-S0E65ESR MINGW64 ~
$ docker run -d -p 9090:80 --name choque mi-sitio:v2
abf569b8a2d98b3c3e481f83c4f8f8968af191c5c93cfc8c9cdb3bcf7997861b

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (a7b5aa1309b45465f326de9b5ff146f5b8017b50e5fd781dc51254fbefe94eaf): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information

# Preguntas
# POR QUE NO CAMBIO LA PAGINA SIN RECONSTRUIR: 
# las imagenes de docker son inmutables, se modifico el archivo index de manera local, pero el contenedor aun sigue leyendo la copia congelada hasta que se compile una nuevaimagen y se levante un nuevo contenedor.

# QUE COMPARTEN Y QUE NO DOS CONTENEDORES DE LA MISMA IMAGEN:
# el sistema alpine linux, kernel del host y archivos binarios de Nginx; sin embargo, no comparten direccion ip virtual interna, puerto mapeado en maquina host, tienen sistema de archivos independientes en momento de ejecucion.