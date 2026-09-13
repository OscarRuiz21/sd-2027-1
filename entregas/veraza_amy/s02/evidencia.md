# Evidencia S02 - Amy Veraza

## Comandos usados

```bash
docker pull nginx:alpine
docker build -t mi-sitio-veraza:s02 entregas/veraza_amy/s02
docker run -d -p 9090:80 --name sitio mi-sitio-veraza:s02
docker run -d -p 9090:80 --name choque mi-sitio-veraza:v2
docker build -t mi-sitio-veraza:v2 entregas/veraza_amy/s02
docker rm -f choque
docker run -d -p 9091:80 --name sitio2 mi-sitio-veraza:v2
docker ps
```

## Construccion de la imagen

```text
#0 building with "desktop-linux" instance using docker driver
#1 [internal] load build definition from Dockerfile
#1 DONE 0.1s
#2 [internal] load metadata for docker.io/library/nginx:alpine
#2 DONE 0.6s
#5 [1/2] FROM docker.io/library/nginx:alpine
#5 CACHED
#6 [2/2] COPY index.html /usr/share/nginx/html/index.html
#6 DONE 0.1s
#7 exporting to image
#7 naming to docker.io/library/mi-sitio-veraza:v2
#7 DONE 0.1s
```

## `docker ps` con dos contenedores de mi imagen

```text
CONTAINER ID   IMAGE                COMMAND                  CREATED          STATUS          PORTS                                     NAMES
0d0a7b9a2531   mi-sitio-veraza:v2   "/docker-entrypoint.…"   9 seconds ago    Up 7 seconds    0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
f96476054235   mi-sitio-veraza:v2   "/docker-entrypoint.…"   29 seconds ago   Up 28 seconds   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
```

## Error de puerto ocupado

```text
ff8f333b96a96c4e81fafab65e9b3d46374cac286c387c12ee714da83457b1fc
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (e774bfb30bf9399304bc10b2fa6693427e4c837155acad6413dbfc8fdf4900d0): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information
```

## Por que no cambio la pagina sin reconstruir

No cambio porque el contenedor estaba corriendo una imagen que ya habia copiado
una version anterior de `index.html`. Aunque yo edite el archivo en mi carpeta,
eso no modifica automaticamente el archivo que quedo adentro de la imagen ni el
contenedor ya creado. Para ver el cambio tuve que reconstruir la imagen y crear
un contenedor nuevo a partir de esa version.

## Que comparten y que no comparten dos contenedores de la misma imagen

Dos contenedores de la misma imagen comparten la plantilla base, es decir, las
capas de solo lectura con el sistema y los archivos que fueron empaquetados en
la imagen. Lo que no comparten es su estado en ejecucion: cada contenedor tiene
sus propios procesos, su propia capa escribible, su propio nombre, su propio ID
y puede publicar puertos distintos.
