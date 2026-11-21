Evidencia - Sesion 02

Comando utilizados.

docker pull nginx:alpine. Descarga la imagen base de Nginx desde Docker Hub.
docker images Lista las imagenes disponibles en el almacenamiento de nuestras maquinas.
docker build -t mi-sitio . Compila las instrucciones del Dockerfile en capas para producir una nueva imagen, identificada con el tag mi-sitio.
docker ps Muestra los contenedores que se estan ejecutando.
MODIFICACION DEL INDEX.HTML
docker build -t mi-sitio:v2 . Reconstruye la imagen agregando las modificaciones recientes, asignandole el tag v2 para distinguirla de la version anterior.
docker stop sitio Detiene el contenedor sitio sin eliminarlo.
docker rm sitio  Elimina el contenedor sitio que previamente detenido.
docker run -d -p 9090:80 --name sitio mi-sitio:v2  Levanta un contenedor a partir de la imagen v2.
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2 Levanta un segundo contenedor de la misma imagen en otro puerto.

Docker ps

oswal@OswaldoF MINGW64 ~/onedrive/documentos/github/sd-2027-1/entregas/flores_oswaldo/s02 (s02-flores)
$ docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
65f3768720f8   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago    Up 2 minutes    0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio-v2
70e59e27fb93   mi-sitio       "/docker-entrypoint.…"   8 minutes ago    Up 8 minutes    0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
c6275603a293   nginx:alpine   "/docker-entrypoint.…"   16 minutes ago   Up 16 minutes   0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
fdf7301df79d   alpine         "sh"                     17 minutes ago   Up 17 minutes
                              cool_wescoff
1f0115e835fd   nginx:alpine   "/docker-entrypoint.…"   18 minutes ago   Up 18 minutes   80/tcp                                    web2
964038e96cb4   nginx:alpine   "/docker-entrypoint.…"   18 minutes ago   Up 12 minutes   80/tcp                                    web1


Error de puerto ocupado.

C:oswal@OswaldoF MINGW64 ~/onedrive/documentos/github/sd-2027-1/entregas/flores_oswaldo/s02>docker run -d -p 9090:80 --name sitio3 mi-sitio:v2
3e5753d4ca1734fb5b2ed7670bb872ac30da8ee8ee32df54a6ff63ea6c8be5e0

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio3 (335fbbddace0944b30421a97a2b44a09aa6b25444d75239fc94359e1757d1561): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information

C:oswal@OswaldoF MINGW64 ~/onedrive/documentos/github/sd-2027-1/entregas/flores_oswaldo/s02>

El error ocurre porque el puerto 9090 ya tenia un contenedor asignado, y Docker impide que dos contenedores compartan el mismo puerto del host al mismo tiempo.

a) ¿Por que no cambio la pagina sin reconstruir? 

No cambio porque el index.html que se edita en mi carpeta no es el que esta usando el contenedor. Cuando se hace el build, ese archivo se copia adentro de la imagen y ahi se queda fijo. Aunque se siga modificado, el contenedor sigue sirviendo la copia vieja que quedo guardada desde ese momento. Para que se vea el cambio se tiene que reconstruir la imagen y crear un contenedor nuevo.



b) ¿Que comparten y que no los dos contenedores de la misma imagen?

Los dos contenedores que levante comparten exactamente el mismo contenido porque nacieron de la misma imagen, o sea el mismo HTML, la misma configuracion de nginx, todo igual por dentro. Lo que no comparten es su ejecucion, ya que cada uno corre por separado con su propio nombre y su propio puerto, por eso pude tenerlos prendidos al mismo tiempo sin que uno afectara al otro, y por eso al correr docker ps me aparecen como dos entradas distintas.
