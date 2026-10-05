# Evidencia - S02

## 1. Comandos utilizados
Durante esta práctica utilicé los siguientes comandos:
- `docker pull nginx:alpine`
- `docker build -t mi-sitio .`
- `docker build -t mi-sitio:v2 .`
- `docker run -d -p 9090:80 --name sitio mi-sitio:v2`
- `docker run -d -p 9091:80 --name sitio2 mi-sitio:v2`
- `docker stop sitio`
- `docker rm sitio`
- `docker ps`
- `docker logs sitio`
- `docker exec -it sitio sh`

## 2. Salida de `docker ps` (Dos contenedores corriendo)
$ docker ps
CONTAINER ID   IMAGE                                COMMAND                  CREATED          STATUS                    PORTS
        NAMES
ad720ce53838   mi-sitio:v2                          "/docker-entrypoint.…"   7 seconds ago    Up 7 seconds              0.0.0.0:9091->80/tcp, [::]:9091->80/tcp       sitio2
9c812f56d860   mi-sitio:v2                          "/docker-entrypoint.…"   3 minutes ago    Up 3 minutes              0.0.0.0:9090->80/tcp, [::]:9090->80/tcp       sitio
a0200b10b456   nginx:alpine                         "/docker-entrypoint.…"   24 minutes ago   Up 24 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp       miweb
c9a93ea23be0   nginx:alpine                         "/docker-entrypoint.…"   30 minutes ago   Up 30 minutes             80/tcp
        web2
c2215966ff46   nginx:alpine                         "/docker-entrypoint.…"   30 minutes ago   Up 28 minutes             80/tcp
        web1
7c38f1619d89   ghcr.io/open-webui/open-webui:main   "bash start.sh"          12 days ago      Up 43 minutes (healthy)   0.0.0.0:3000->8080/tcp, [::]:3000->8080/tcp   open-webui

## 3. Error de puerto ocupado
$ docker run -d -p 9090:80 --name choque mi-sitio:v2
79ba401978e55721efec5e8bcc20eaf612f3a239c7c63a8fdf707ad7fc69f0c3
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (cce41b845f31550b2e576bed8ec5c14cee9d038aefe3391ec509de686a45f2db): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information

## 4. Respuestas a las preguntas

**¿Por qué no cambió la página sin reconstruir?**
Porque la imagen funciona como una plantilla de solo lectura. Al ejecutar el comando `docker build`, Docker toma una "foto" de los archivos y los congela en una capa. El contenedor que ya estaba corriendo lee esa versión congelada desde el interior de su burbuja, por lo que ignora los cambios que hice en vivo en el sistema de mi computadora. Para ver los cambios, fue necesario hornear una nueva imagen y levantar un contenedor nuevo.

**¿Qué comparten y qué no comparten dos contenedores de la misma imagen?**
* **Lo que comparten:** Comparten exactamente la misma imagen base, es decir, el mismo sistema operativo mínimo, las mismas librerías y los mismos archivos iniciales (como el `index.html`). Además, al estar en la misma computadora, comparten el kernel de mi máquina host.
* **Lo que no comparten:** Están totalmente aislados en su ejecución. No comparten su memoria, ni sus procesos en vivo, ni su capa de escritura (si uno modifica un archivo interno, el otro no lo nota). Tampoco comparten el puerto de salida hacia mi computadora, ya que forzosamente tuvimos que asignarles puertos distintos (9090 y 9091).
