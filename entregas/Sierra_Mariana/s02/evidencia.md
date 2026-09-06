# Evidencia - Mariana Sierra

## docker ps (Misión 3)
CONTAINER ID   IMAGE         COMMAND                  CREATED              STATUS              PORTS                                     NAMES
b2ec23417769   mi-sitio:v2   "/docker-entrypoint.…"   5 seconds ago        Up 4 seconds        0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
a798a33cb81f   mi-sitio:v2   "/docker-entrypoint.…"   About a minute ago   Up About a minute   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
cae0279e1371   nginx:alpine  "/docker-entrypoint.…"   45 minutes ago       Up 45 minutes       0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
05ef3f3c5834   nginx:alpine  "/docker-entrypoint.…"   7 hours ago          Up 7 hours          80/tcp                                    web2
130e5a12f493   nginx:alpine  "/docker-entrypoint.…"   7 hours ago          Up 7 hours          80/tcp                                    web1

## Error de puerto ocupado
docker: Error response from daemon: driver failed programming external connectivity on endpoint choque: Bind for 0.0.0.0:8080 failed: port is already allocated.

## Respuestas
**¿Por qué no cambió la página sin reconstruir?**
Porque las imágenes de Docker son inmutables. El comando 'build' toma una "foto" de los archivos en ese instante y la sella. Aunque yo edite el archivo index.html en mi Windows, el contenedor sigue ejecutando la copia congelada en su interior. Fue necesario hacer un build nuevo (v2) para actualizar la imagen.

**¿Qué comparten y qué no dos contenedores de la misma imagen?**
Comparten exactamente los mismos archivos iniciales, el sistema operativo base y la configuración de la imagen. NO comparten su estado de ejecución, su ID único, su memoria RAM, ni los puertos físicos de la máquina host (uno ocupa el 9090 y otro el 9091).
