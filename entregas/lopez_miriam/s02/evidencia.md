# **Evidencia S02 - Docker**



#### Comandos utilizados durante la práctica

Durante la práctica utilicé diferentes comandos de Docker para descargar imágenes, crear y administrar contenedores, revisar su estado y construir mi propia imagen

Los principales comandos utilizados fueron:

* `docker pull nginx:alpine`: descarga la imagen de Nginx basada en Alpine.
* `docker images`: muestra las imágenes de Docker disponibles en mi computadora.
* `docker run`: crea y ejecuta un contenedor utilizando una imagen.
* `docker ps`: muestra los contenedores que se encuentran actualmente en ejecución.
* `docker ps -a`: muestra todos los contenedores, incluyendo aquellos que están detenidos.
* `docker stop`: detiene un contenedor que se encuentra en ejecución.
* `docker start`: inicia nuevamente un contenedor que había sido detenido.
* `docker exec`: permite ejecutar comandos dentro de un contenedor que ya está funcionando.
* `docker logs`: permite consultar los registros generados por un contenedor.
* `docker rm`: elimina un contenedor.
* `docker build`: construye una imagen utilizando las instrucciones definidas en un Dockerfile.



#### Creación de contenedores con Nginx

Primero utilicé la imagen `nginx:alpine` para crear diferentes contenedores.

Se crearon los contenedores:

* web1
* web2 



Ambos fueron creados utilizando la misma imagen `nginx:alpine`.

Esto me permitió comprobar que una misma imagen de Docker puede ser utilizada para crear diferentes contenedores independientes

También utilicé los comandos `docker stop` y `docker start` para detener e iniciar nuevamente el contenedor `web1`, comprobando que detener un contenedor no significa eliminarlo.



#### Publicación de un puerto

Después creé el contenedor `miweb` utilizando la imagen `nginx:alpine` y publiqué el puerto 80 del contenedor mediante el puerto 8080 de mi computadora

El contenedor quedó asociado de la siguiente manera:

&#x09;Puerto 8080 del host -> Puerto 80 del contenedor

Al ingresar desde el navegador a:

&#x09;http://localhost:8080

me fue posible ver la página predeterminada de Nginx.



#### Error de puerto ocupado

Para comprobar qué sucede cuando dos contenedores intentan utilizar el mismo puerto del host, ejecuté otro contenedor llamado `choque` intentando utilizar nuevamente el puerto 8080.

El comando utilizado fue:

&#x09;docker run -d -p 8080:80 --name choque nginx:alpine

Docker creó inicialmente el identificador:

&#x09;b354012bae95bfaf5cadf4de56d3aa81e37544df3f9b148023ef1f6cf9f6a9a4

pero después me mostró el siguiente error:

&#x09;docker: Error response from daemon: failed to set up container networking: driver failed programming external 	connectivity on endpoint choque (8f9bddfd981e24fbb82276f24acb52ec69dfa23d4ddc6572b97620e6757d9fa6): Bind for 	0.0.0.0:8080 failed: port is already allocated

Este error ocurrió porque el puerto `8080` de mi computadora ya estaba siendo utilizado por el contenedor `miweb`.

Fue por ello que Docker no pudo asociar un segundo contenedor al mismo puerto del host al mismo tiempo.



#### Creación de mi propia imagen

Para crear mi propia imagen utilicé un archivo llamado `Dockerfile`.

El contenido utilizado fue:

&#x09;FROM nginx:alpine

&#x09;COPY index.html /usr/share/nginx/html/index.html

&#x09;EXPOSE 80

La instrucción `FROM` utiliza `nginx:alpine` como imagen base.

La instrucción `COPY` copia mi archivo `index.html` dentro del directorio que Nginx utiliza para servir páginas web.

Finalmente, `EXPOSE 80` indica que el servicio utiliza el puerto 80 dentro del contenedor.

La imagen fue construida utilizando `docker build`.

Después se creó una segunda versión de la imagen:

&#x09;mi-sitio:v2



#### Página web creada

Dentro del archivo `index.html` agregué mi nombre y una tabla con cinco comandos de Docker que aprendí durante la práctica.

Los comandos incluidos fueron:

1\. `docker pull`

2\. `docker images`

3\. `docker run`

4\. `docker ps`

5\. `docker build`

La página fue ejecutada utilizando un contenedor creado desde mi propia imagen.



#### ¿Por qué la página no cambió después de modificar index.html?

Después de construir la primera imagen y crear el contenedor, modifiqué el archivo `index.html` de mi computadora, pero al actualizar la página del navegador los cambios no aparecieron rapid, esto ocurre porque el archivo `index.html` fue copiado dentro de la imagen durante la ejecución de `docker build`.

Una imagen de Docker mantiene el contenido con el que fue construida, es por ello que modificar posteriormente el archivo original que se encuentra en mi computadora no modifica automáticamente la imagen que ya existe.

Para que los cambios fueran visibles fue necesario volver a construir la imagen.

Se creó una nueva versión:

&#x09;mi-sitio:v2

Después eliminé el contenedor anterior y creé un nuevo contenedor utilizando esta nueva versión de la imagen.

Con esto los cambios realizados los pude ver correctamente.



#### Dos contenedores utilizando la misma imagen

Finalmente utilicé la imagen:

&#x09;mi-sitio:v2

para crear dos contenedores distintos:

&#x09;sitio

&#x09;sitio2

El contenedor `sitio` utiliza:

&#x09;Puerto 9090 del host -> Puerto 80 del contenedor

El contenedor `sitio2` utiliza:

&#x09;Puerto 9091 del host -> Puerto 80 del contenedor

De esta forma fue posible acceder a la misma página mediante:

&#x09;http://localhost:9090

y:

&#x09;http://localhost:9091



#### Salida de docker ps

Al finalizar esta parte de la práctica ejecuté:

&#x09;docker ps

y obtuve la siguiente salida:



CONTAINER ID   IMAGE          STATUS        PORTS                                      NAMES

96c5ca55a31a   mi-sitio:v2    Up            0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

30287340e0fa   mi-sitio:v2    Up            0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio

246eb99e4545   nginx:alpine   Up            0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp   miweb

6c25450fcfed   nginx:alpine   Up            80/tcp                                     web2

361e3ef383a7   nginx:alpine   Up            80/tcp                                     web1

fc4c53c5ea1e   postgres:18    Up            0.0.0.0:5432->5432/tcp, \[::]:5432->5432/tcp postgres\_bda





En esta salida se puede observar principalmente que los contenedores `sitio` y `sitio2` utilizan exactamente la misma imagen `mi-sitio:v2`, pero cada uno funciona de manera independiente y utiliza un puerto diferente 



#### ¿Qué comparten y qué no comparten dos contenedores de la misma imagen?

Dos contenedores creados a partir de la misma imagen comparten la misma base de inicio

En este caso, `sitio` y `sitio2` fueron creados utilizando `mi-sitio:v2`, por lo que al incio contienen la misma página web, los mismos archivos y la misma configuración 

Pero los contenedores son independientes.

Por ejemplo, aunque `sitio` y `sitio2` utilizan la misma imagen, `sitio` está disponible en el puerto `9090` y `sitio2` está disponible en el puerto `9091`.





#### Conclusión

Durante esta práctica aprendí la diferencia entre una imagen y un contenedor de Docker. Una imagen funciona como una base o plantilla a partir de la cual pueden crearse diferentes contenedores, también aprendí a descargar imágenes, crear, detener, iniciar y eliminar contenedores, consultar sus registros y publicar puertos para acceder a los servicios desde mi computadora.

Para finalizar, comprobé que una misma imagen puede ejecutar varios contenedores independientes utilizando diferentes puertos del host.

