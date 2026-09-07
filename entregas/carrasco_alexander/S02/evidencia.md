\# Evidencia Docker  Misiones 2, 3 y 4



\## Misión 2 — La trampa



\### Comandos utilizados



```text

docker ps

docker build -t mi-sitio:v2 .

docker rm -f sitio 

docker run -d -p 9090:80 --name sitio mi-sitio:v2

docker ps

```

A grandes rasgos, el comando docker rm -f sitio sirve para eliminar el contenedor llamado sitio. La opción -f fuerza su eliminación, por lo que si el contenedor estaba ejecutándose, Docker primero lo detiene y después lo elimina. En mi caso lo utilicé para eliminar el contenedor anterior y posteriormente crear uno nuevo utilizando la imagen actualizada mi-sitio:v2.



\### ¿Por qué no cambió la página sin reconstruir?



La página no cambió después de modificar el archivo `index.html` porque el contenedor estaba utilizando la versión del archivo que había sido copiada dentro de la imagen cuando ejecuté `docker build`.



Lo que entendí fue que el cambio que hice en mi computadora no modifica automáticamente la imagen ni el contenedor que ya estaba creado.



Por eso tuve que reconstruir la imagen usando `docker build` para crear `mi-sitio:v2` y después eliminar el contenedor anterior y crear uno nuevo utilizando la nueva imagen.



\---



\## Misión 3 — Multiplícate



\### Comandos utilizados



```text

docker run -d -p 9090:80 --name sitio mi-sitio:v2

docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

docker ps

```



\### Salida de `docker ps`



```text

CONTAINER ID   IMAGE          COMMAND                  CREATED         STATUS         PORTS                                     NAMES

cf5c0d6d6e62   mi-sitio:v2    "/docker-entrypoint.…"   8 minutes ago   Up 8 minutes   0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

32d666e3ad2f   mi-sitio:v2    "/docker-entrypoint.…"   6 hours ago     Up 6 hours     0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio```



Los dos contenedores utilizan la misma imagen `mi-sitio:v2`, pero cada uno utiliza un puerto diferente de mi computadora.



\---



\## Misión 4 — Evidencia del puerto ocupado



\### Comando utilizado



```text

docker run -d -p 9090:80 --name choque mi-sitio:v2

```



\### Error obtenido





```text

PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\s02> docker run -d -p 9090:80 --name choque mi-sitio:v2



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: Conflict. The container name "/choque" is already in use by container "8c640562a6d6d77cd45c3ca7de08fd2c81cf7ef834c3458e10ca501bead3d42e". You have to remove (or rename) that container to be able to reuse that name.



Run 'docker run --help' for more information

PS C:\\Users\\eluve\\repos\\sd-2027-1\\entregas\\carrasco\_alexander\\s02>

```



\### ¿Qué comparten dos contenedores de la misma imagen?



Dos contenedores creados a partir de la misma imagen comparten la misma base de archivos, configuración y aplicación que contiene la imagen.



Sin embargo, cada contenedor es una instancia independiente.



\### ¿Qué NO comparten?



No comparten su estado de ejecución ni los cambios realizados dentro de cada contenedor. Cada uno tiene su propio entorno y puede ejecutarse de manera independiente.



En este ejercicio los dos contenedores utilizan la misma imagen, pero están publicados en diferentes puertos: `9090` y `9091`.



\---



\## Conclusión



Con estas prácticas entendí que una imagen de Docker funciona como una plantilla a partir de la cual se pueden crear diferentes contenedores.



También aprendí que modificar un archivo después de construir una imagen no modifica automáticamente los contenedores existentes, por lo que es necesario reconstruir la imagen cuando se quieren incorporar esos cambios.



Durante esta práctica aprendí varias cosas importantes, como ejecutar contenedores utilizando comandos de Docker, revisar los contenedores que están funcionando y entender los errores que pueden aparecer. Por ejemplo, aprendí que si intento utilizar un puerto que ya está ocupado por otro contenedor, Docker muestra un error porque dos contenedores no pueden utilizar al mismo tiempo el mismo puerto de la máquina.



También entendí que una misma imagen puede utilizarse para crear varios contenedores. Por ejemplo, en la Misión 3 pude crear dos contenedores utilizando la misma imagen, pero cada uno funcionaba de manera independiente y utilizaba un puerto diferente.



Ahora entiendo que la imagen es como una plantilla que contiene la configuración y los archivos necesarios para crear un contenedor. El contenedor es una instancia de esa imagen que se encuentra funcionando. Aunque puedo realizar cambios dentro de un contenedor, esos cambios no modifican automáticamente la imagen original ni los demás contenedores creados a partir de ella.



Además, aprendí a detener y eliminar contenedores. En la Misión 2 tuve que eliminar el contenedor anterior para poder crear uno nuevo con la versión actualizada de la imagen. También encontré un error porque ya existía un contenedor con el mismo nombre, y aprendí que Docker no permite tener dos contenedores con exactamente el mismo nombre.

