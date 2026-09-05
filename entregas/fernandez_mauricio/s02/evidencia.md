Evidencia - Lab S02 Docker, día uno



Alumno: Mauricio Fernandez

Directorio: entregas/fernandez\_mauricio/s02



1\. Comandos utilizados



Los comandos más utilizados y/o útiles a mi parecer fueron:



docker ps: muestra los contenedores que están ejecutándose.

docker images: muestra las imágenes disponibles localmente.

docker build: construye una imagen a partir de un Dockerfile.

docker run: crea y ejecuta un contenedor a partir de una imagen.

docker stop: detiene un contenedor que está ejecutándose.


2. docker ps

A continuación, se muestra la evidencia de los contenedores de mi imagen corriendo haciendo uso del comando correspondiente:

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s02> docker ps

CONTAINER ID   IMAGE          COMMAND                  CREATED             STATUS             PORTS                                     NAMES

d3e496f9d266   mi-sitio:v2    "/docker-entrypoint.…"   7 seconds ago       Up 6 seconds       0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

95b17eaf42c3   mi-sitio       "/docker-entrypoint.…"   About an hour ago   Up About an hour   0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio

b5472659b004   nginx:alpine   "/docker-entrypoint.…"   2 hours ago         Up 2 hours         0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp   miweb

34230569218d   nginx:alpine   "/docker-entrypoint.…"   23 hours ago        Up 23 hours        80/tcp                                    web2

a5200b94d4ea   nginx:alpine   "/docker-entrypoint.…"   23 hours ago        Up 22 hours        80/tcp                                    web1


3. Error de puerto ocupado



Para producir el error, intenté ejecutar otro contenedor utilizando el puerto 9090, que ya estaba ocupado por el primer contenedor de mi imagen creada:

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\s02> docker run -d --name choque9090 -p 9090:80 mi-sitio:v2

00f8c0b2c1678233ddb43051bcfc32fb39f2c53b000f48e59ee2ef96513e6885



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque9090 (af52410ab5d714bce0f269441ce4b4c03da08bf17a3dc443db66c878391bf95b): Bind for 0.0.0.0:9090 failed: port is already allocated



Run 'docker run --help' for more information


4. ¿Por qué no cambió la página sin reconstruir?



La página no cambió porque el contenedor estaba utilizando una imagen que ya había sido construida anteriormente. Al modificar index.html en mi computadora, solamente cambié el archivo local, pero el contenedor seguía utilizando la copia del archivo que estaba almacenada dentro de la imagen de mi sitio.



Por eso fue necesario reconstruir la imagen con:



docker build -t mi-sitio:v2 .



De esta manera se creó una nueva imagen que hacía uso de la versión modificada de index.html.


5. ¿Qué comparten y qué no dos contenedores de la misma imagen?



Dos contenedores creados a partir de la misma imagen comparten la misma base de archivos, configuración y contenido inicial proporcionado por esa imagen. Sin embargo, cada contenedor es una instancia independiente, por lo que tienen su propio estado, procesos, red y cambios realizados durante su ejecución.



En esta práctica, sitio y sitio2 pueden utilizar el mismo puerto interno 80, pero se publicaron mediante puertos diferentes del equipo:



sitio  → puerto interno 80 → localhost:9090

sitio2 → puerto interno 80 → localhost:9091



Por lo tanto, aunque parten de imágenes relacionadas y ejecutan la misma aplicación, cada contenedor funciona de manera independiente.


