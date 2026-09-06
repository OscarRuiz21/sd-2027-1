# Evidencia S02 — Estrella Mendoza

## Comandos que usé
docker pull nginx:alpine
docker run -d --name web1 nginx:alpine
docker build -t mi-sitio .
docker run -d -p 9090:80 --name sitio mi-sitio
docker build -t mi-sitio:v2 .
docker rm -f sitio
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

## docker ps con mis dos contenedores
PS C:\Users\Billie Jean\sd-2027-1\entregas\mendoza_estrella\s02> docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
774af65328d2   mi-sitio:v2    "/docker-entrypoint.…"   20 seconds ago   Up 19 seconds   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
0d841869d85a   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago    Up 2 minutes    0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
054b0a6c885c   nginx:alpine   "/docker-entrypoint.…"   14 hours ago     Up 14 hours     0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
c768573d5500   nginx:alpine   "/docker-entrypoint.…"   14 hours ago     Up 14 hours     80/tcp                                    web2
4b85c3c315dc   nginx:alpine   "/docker-entrypoint.…"   14 hours ago     Up 14 hours     80/tcp                                    web1

## Error que me salió por puerto ocupado
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (2acac8f3e3747e85814697a1f298aea17d1bd24671d55f93daeeab327b446126): Bind for 0.0.0.0:9090 failed: port is already allocated

## Preguntas

**¿Por qué no cambió la página sin reconstruir?**
Porque la imagen es de solo lectura, una vez que se construye con docker build el archivo index.html queda congelado dentro de las capas de la imagen. Editar el archivo en mi disco no afecta lo que ya está empaquetado — el contenedor sirve lo que quedó guardado en la imagen, no mi carpeta actual. Para que el cambio se vea, hay que reconstruir la imagen y recrear el contenedor.

**¿Qué comparten y qué no dos contenedores de la misma imagen?**
Comparten la misma plantilla base, es decir el mismo sistema de archivos de solo lectura, la misma aplicación y configuración que trae la imagen. Cada uno tiene su propio proceso corriendo, su propia capa de escritura (por si algo cambia en tiempo de ejecución), y pueden estar en puertos distintos del host.