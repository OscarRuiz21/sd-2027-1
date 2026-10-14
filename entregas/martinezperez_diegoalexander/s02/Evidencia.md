# Evidencia S02 - Primer contenedor e imagen propia

## Misión 1

Modifiqué mi página `index.html` agregando mi nombre y una tabla con los cinco comandos de Docker que más utilicé.

Los comandos que seleccioné fueron:

- `docker build`: construye una imagen utilizando el Dockerfile.
- `docker images`: muestra las imágenes disponibles en Docker.
- `docker run`: crea y ejecuta un contenedor a partir de una imagen.
- `docker ps`: muestra los contenedores que están ejecutándose.
- `docker exec`: permite ejecutar comandos dentro de un contenedor activo.

## Misión 2

Primero modifiqué el archivo `index.html` mientras el contenedor `sitio` estaba ejecutándose.

La página no cambió porque el contenedor utiliza la copia de `index.html` que fue introducida en la imagen durante el `docker build`. Modificar el archivo original en mi computadora no modifica automáticamente la imagen ni el contenedor que ya está ejecutándose.

Después reconstruí la imagen utilizando:

docker build -t mi-sitio:v2 .

Después eliminé el contenedor anterior y levanté uno nuevo utilizando la versión `v2`.

## Misión 3

Ejecuté dos contenedores utilizando la misma imagen:

docker run -d -p 9090:80 --name sitio mi-sitio:v2

docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

### docker ps

CONTAINER ID   IMAGE          COMMAND                  CREATED              STATUS              PORTS                                     NAMES
7e7b2f9f18bb   mi-sitio:v2    "/docker-entrypoint.…"   26 seconds ago       Up 26 seconds       0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
fccd771d796d   mi-sitio:v2    "/docker-entrypoint.…"   About a minute ago   Up About a minute   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
d74cfbcb8420   nginx:alpine   "/docker-entrypoint.…"   28 minutes ago       Up 28 minutes       0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
dee2c7834a27   db35bfc6b295   "/docker-entrypoint.…"   8 days ago           Up 29 minutes       80/tcp                                    web1

## Error de puerto ocupado

Intenté utilizar nuevamente el puerto 9090:

docker run -d -p 9090:80 --name sitio3 mi-sitio:v2

### Error obtenido
What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: Conflict. The container name "/sitio" is already in use by container "640be777ebfaac3b3a6b152885a4c3112ea36523caece05b0769979e7f1a28ab". You have to remove (or rename) that container to be able to reuse that name.

## ¿Por qué no cambió la página sin reconstruir?

No cambió porque el archivo que modifiqué en mi computadora es diferente de la copia que ya estaba dentro de la imagen y del contenedor. Para que el cambio formara parte de la aplicación tuve que construir una nueva imagen y crear un nuevo contenedor.

## ¿Qué comparten y qué no dos contenedores de la misma imagen?

Los dos contenedores parten de la misma imagen, por lo que tienen los mismos archivos, configuración y versión de la aplicación. Sin embargo, cada contenedor es una instancia independiente y tiene su propio proceso, red y estado. También pueden publicarse mediante puertos diferentes.

## Conclusión

Con este ejercicio aprendí a construir una imagen propia utilizando Docker, ejecutarla como contenedor y crear varias instancias de la misma imagen.