

# Evidencia del laboratorio S02: Docker, día uno



## 1. Comandos utilizados

```bash
# Construcción y ejecución de la primera versión
docker build -t mi-sitio:v1 .
docker run -d -p 9090:80 --name sitio mi-sitio:v1

# Inspección del contenedor
docker ps
docker exec -it sitio sh
ls /usr/share/nginx/html
exit
docker logs sitio

# Reconstrucción después de modificar index.html
docker build -t mi-sitio:v2 .
docker stop sitio
docker rm sitio
docker run -d -p 9090:80 --name sitio mi-sitio:v2

# Creación de una segunda instancia de la misma imagen
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2
docker ps
```

## 2. Evidencia de los dos contenedores

Los dos contenedores fueron creados con la imagen `mi-sitio:v2`. Cada uno usa
un puerto diferente de la computadora para evitar conflictos.
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
b69cfc09234c   nginx:alpine   "/docker-entrypoint.…"   18 minutes ago   Up 18 minutes   0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
276e5cc5a555   nginx:alpine   "/docker-entrypoint.…"   32 minutes ago   Up 32 minutes   80/tcp                                    web2
bca12fb42521   nginx:alpine   "/docker-entrypoint.…"   32 minutes ago   Up 21 minutes   80/tcp                                    web1


## 3. Error de puerto ocupado

Con el contenedor `sitio` usando el puerto 9090, se intentó crear otro
contenedor con el mismo puerto:

```bash
docker run -d -p 9090:80 --name choque mi-sitio:v2
```

Docker mostró un error equivalente al siguiente:

```text
docker: Error response from daemon: driver failed programming external connectivity on endpoint choque:
Bind for 0.0.0.0:9090 failed: port is already allocated.
```

El error apareció porque un puerto de la computadora solo puede estar asignado
a un contenedor o proceso a la vez. En este caso, el puerto 9090 ya pertenecía
al contenedor `sitio`. Después de guardar la evidencia se eliminó el contenedor
fallido con:

```bash
docker rm choque
```

## 4. ¿Por qué no cambió la página sin reconstruir la imagen?

La página no cambió porque `index.html` se copió al interior de la imagen al
ejecutar `docker build`. El contenedor que ya estaba activo continuó usando la
versión del archivo almacenada en `mi-sitio:v1`; no estaba conectado al archivo
original de la computadora. Por ello fue necesario construir `mi-sitio:v2`,
detener y eliminar el contenedor anterior, y crear uno nuevo con la imagen
actualizada.

## 5. ¿Qué comparten y qué no comparten dos contenedores de la misma imagen?

Los dos contenedores comparten la misma imagen de origen y, por lo tanto, parten
del mismo contenido y de la misma configuración de nginx. Sin embargo, cada
contenedor es una instancia independiente: tiene sus propios procesos, una capa
de escritura propia, su estado, su nombre, su identidad de red y su asignación
de puertos. Un cambio realizado dentro de un contenedor no modifica
automáticamente al otro ni altera la imagen original.

## 6. Conclusión

La práctica permitió comprobar que una imagen funciona como una plantilla
inmutable y que de ella se pueden crear varios contenedores independientes. La
reconstrucción de la imagen fue necesaria para incorporar los cambios del
archivo HTML, mientras que el uso de los puertos 9090 y 9091 permitió ejecutar
dos instancias de la misma aplicación al mismo tiempo.

