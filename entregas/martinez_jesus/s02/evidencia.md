# Evidencia Práctica S02 - Docker, dia uno: de cero a tu propia imagen
**Nombre:** Jesús Martínez Trejo

## 1. Comandos utilizados
### 1.1 Construcción de la primera versión de la página
`docker build -t mi-sitio .`

`docker run -d -p 9090:80 --name sitio mi-sitio`

### 1.2 Actualización del sitio generando otra imagen, eliminando el contenedor antiguo y creando el nuevo
`docker build -t mi-sitio:v2 .`

`docker rm -f sitio`

`docker run -d -p 9090:80 --name sitio mi-sitio:v2`

### 1.3 Creación de una replica del sitio
`docker run -d -p 9091:80 --name sitio_clon mi-sitio:v2`


## 2. Contenedores corriendo (docker ps)
```text
CONTAINER ID   IMAGE          COMMAND                  CREATED         STATUS         PORTS                                     NAMES
98cdb06dbb5a   mi-sitio:v2    "/docker-entrypoint.…"   6 seconds ago   Up 6 seconds   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio_clon
66a1b361d2d9   mi-sitio:v2    "/docker-entrypoint.…"   3 minutes ago   Up 3 minutes   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
f26993bd2720   nginx:alpine   "/docker-entrypoint.…"   2 hours ago     Up 2 hours     0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
c6a31dc4b6d0   nginx:alpine   "/docker-entrypoint.…"   7 days ago      Up 3 hours     80/tcp                                    web2
db5640c6ab44   nginx:alpine   "/docker-entrypoint.…"   7 days ago      Up 3 hours     80/tcp                                    web1
```

## 3. Error de puerto ocupado
Para producir este error intencionalmente, intenté levantar un segundo contenedor (choque) usando el puerto 8080, que ya estaba asignado al contenedor miweb, usando el comando: `docker run -d -p 8080:80 --name choque nginx:alpine`
Esto generó un error al intentar un puerto de la maquina host que ya estaba utilizado

```text
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (2669fbd6e60e8f55841e500152c1b960528a40a431d0af76b3732c252feb0575): Bind for 0.0.0.0:8080 failed: port is already allocated
```


## 4. Preguntas
**¿Por qué no cambió la página sin reconstruir?**

Porque las imágenes de Docker son inmutables, es decir, funcionan como una captura estática del estado, los archivos y las configuraciones al momento de su creación. De esta manera el contenedor ejecuta un entorno aislado basado en una fotografía exacta (generada durante el comando build a partir del Dockerfile). Al no realizar consultas dinámicas al disco de la máquina anfitriona, el contenedor ignora cualquier cambio posterior en los archivos originales.

**¿Qué comparten y qué no comparten dos contenedores de la misma imagen?**

Comparten la imagen base exacta que incluye el sistema operativo, las bibliotecas y las configuraciones iniciales, así como el Kernel del sistema operativo anfitrión donde se ejecuta docker.

No comparten cualquier archivo creado o modificado internamente, su espacio en memoria RAM, sus PIDs ni la IP privada asignada por Docker. Al estar en ambientes de red y ejecución aislados, tampoco pueden comunicarse entre ellos inicialmente.