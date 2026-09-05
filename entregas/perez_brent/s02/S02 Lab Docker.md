# Evidencia de Práctica Docker

## 1. Comandos Utilizados
* `docker build -t mi-sitio:v2 .`
* `docker stop sitio`
* `docker rm sitio`
* `docker run -d -p 9090:80 --name sitio mi-sitio:v2`
* `docker run -d -p 9091:80 --name sitio_clon mi-sitio:v2`
* `docker ps`

## 2. Salida de docker ps

```shell
brent@pc-bda-bpp:~/SDistribuidos/sd-2027-1/entregas/perez_brent/s02$ docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
52696a555487   mi-sitio:v2    "/docker-entrypoint.…"   32 minutes ago   Up 32 minutes   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio_clon
b918030f3bf1   mi-sitio:v2    "/docker-entrypoint.…"   33 minutes ago   Up 33 minutes   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
ca8f21b1808f   nginx:alpine   "/docker-entrypoint.…"   46 hours ago     Up 43 minutes   0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
1b0be2093b07   nginx:alpine   "/docker-entrypoint.…"   46 hours ago     Up 45 minutes   80/tcp                                    web2
85dca0b19452   nginx:alpine   "/docker-entrypoint.…"   46 hours ago     Up 45 minutes   80/tcp                                    web1
```
## 3. Error de puerto ocupado

```shell
brent@pc-bda-bpp:~/SDistribuidos/sd-2027-1/entregas/perez_brent/s02$ docker run -d -p 9090:80 --name sitio_error mi-sitio:v2 
92eec3a2d649f350f18939b680d23d8b0a2b4bc2d85db88e3b2ce1745d29b299
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio_error (197b95bcf6d3888e9bd54889e971a9385a725ab6dfc20f4d83b6a863a3d1413c): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information

```

## 4. Preguntas
* ¿Por qué no cambió la página sin reconstruir?
Porque las imagenes de Docker son inmutables. Cuando ejecutamos el comando `build`, Docker toma una foto de los archivos en ese instante y lo congela; si modificamos el archivo `index.html` desde el host, eso no alterará los archivos del contenedor ya que los nuevos cambios no los tiene.

* ¿Qué comparten y qué no dos contenedores de la misma imagen?
Lo que comparten es que tienen la misma estructura base, dependencias, librerías y archivos iniciales (el molde). Lo que no comparten son su memoria, sus procesos en ejecuci+on, ni sus puertos de red en el host; además, cualquier archivo que se cree o modifique temporalmente dentro del contenedor vivirá ahí hasta que se apague y no afectará al otro contenedor.

-- Pérez Paitán Brent Armando