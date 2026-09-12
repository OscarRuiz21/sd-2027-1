##### **COMANDOS QUE USÉ**



docker pull nginx:alpine: Para descargar la imagen base.



docker images: Para verificar las imágenes locales descargadas.



docker run -d --name web1 nginx:alpine: Para crear y levantar el primer contenedor en segundo plano.



docker run -it --rm alpine sh: Para crear un contenedor temporal desechable y explorar su terminal interna.



docker run -d -p 8080:80 --name miweb nginx:alpine: Para levantar un contenedor publicando su puerto (80 hacia el 8080) y poder verlo en mi navegador.



docker logs miweb: Para revisar el historial de eventos y accesos del contenedor.



docker exec -it miweb sh: Para entrar a la terminal de un contenedor que ya estaba en ejecución.



docker build -t mi-sitio .: Para construir mi primera imagen personalizada empaquetando tu archivo index.html.



docker ps: Para listar los contenedores activos y revisar sus puertos e IDs.



docker build -t mi-sitio:v2 .: Para reconstruir la imagen en una nueva versión tras actualizar el HTML (Misión 2).



docker rm -f sitio: Para forzar el borrado del contenedor desactualizado.



docker run -d -p 9090:80 --name sitio mi-sitio:v2: Para levantar el contenedor con la página actualizada.



docker run -d -p 9091:80 --name sitio2 mi-sitio:v2: Para levantar el segundo contenedor simultáneo sin choques de puerto (Misión 3).



docker run -d -p 9090:80 --name choque nginx:alpine: Para provocar intencionalmente el error de puerto ocupado (Misión 4)







##### ***EVIDENCIA MISIÓN 3***



emili@BOOK-EV4EELQ1E2 MINGW64 \~/sd-2027-1/entregas/Salgado\_Roman/s02 (entregas\_salgado\_roman)

$ docker ps

CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES

94989a83d4e4   mi-sitio:v2    "/docker-entrypoint.…"   21 seconds ago   Up 20 seconds   0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

9a53057e9419   mi-sitio:v2    "/docker-entrypoint.…"   9 minutes ago    Up 9 minutes    0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio

3d0bac4da95b   nginx:alpine   "/docker-entrypoint.…"   8 hours ago      Up 8 hours      0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp   miweb

2ff59711447b   nginx:alpine   "/docker-entrypoint.…"   9 hours ago      Up 9 hours      80/tcp                                    web2

135c048c45d3   nginx:alpine   "/docker-entrypoint.…"   9 hours ago      Up 9 hours      80/tcp                                    web1





##### **EVIDENCIA ERROR DE PUERTO**



emili@BOOK-EV4EELQ1E2 MINGW64 \~/sd-2027-1/entregas/Salgado\_Roman/s02 (entregas\_salgado\_roman)

$ docker run -d -p 9090:80 --name choque nginx:alpine

76f4d0295d5ca9964247c62f2ab869312653707d4afaeb22a48c05b6709666b5



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (bfe750e31654e96374f02515a31cd366a3eaa9a70dd59c3920369307e1a37cf8): Bind for 0.0.0.0:9090 failed: port is already allocated



Run 'docker run --help' for more information



##### 

##### **¿Por qué no cambió la página sin reconstruir?**



La razón por la que no se actualizan los cambios que realizaba en el index.html es que las imágenes de Docker son inmutables, esto significa que se congelan y funcionan como una fotografía, cuando ejecutamos el comando build se tomó el archivo index.html y fue lo que congeló dentro de la imagen. Como el contenedor se crea a partir de esa imagen congelada y está aislado en una burbuja, no recibe de ninguna manera los nuevos cambios.

##### 

##### **¿Qué comparten y qué no dos contenedores de la misma imagen?**



Ambas comparten la plantilla base, que es la imagen original con sus librerías y estructura inicial. De igual forma el kernel lo comparten, pero solo eso. Lo que no comparten es su contenido, dado que cada contenedor funciona de manera aislada, no comparten su contenido, no existe ningún tipo de comunicación entre ellos. No comparten memoria, estado de ejecución, puertos asignados y tampoco la capa de escritura.

