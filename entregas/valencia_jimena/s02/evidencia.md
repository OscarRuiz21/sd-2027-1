Evidencia - Sesión 02

Comando utilizados.

docker pull nginx:alpine. Descargue la imagen de Ngix.
docker images Liste las imagenes dispobibles
docker build -t mi-sitio . Construi la imagen a partir del Dockerfile
docker ps Liste los contenedores que estan trabajando
MODIFICACIÓN DEL INDEX.HTML
docker build -t mi-sitio:v2 . Construcción de la nueva versión
docker stop sitio Detiene el contenedor anteiror.
docker rm sitio  Elimina el contenedor anteiror.
docker run -d -p 9090:80 --name sitio mi-sitio:v2  Construye la nueva versión de la imagen
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2 Crea un segundo contenedor utilizando la misma imagen en diferente puerto

Docker ps

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s02>docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
b60f27a249bf   mi-sitio:v2    "/docker-entrypoint.…"   5 minutes ago    Up 5 minutes    0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
c94f29bc9bc5   mi-sitio:v2    "/docker-entrypoint.…"   12 minutes ago   Up 12 minutes   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
2fb75edda713   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours      0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
da445bd691c2   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours      80/tcp                                    web2
34e19bfe8dd9   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours      80/tcp                                    web1


Error de puerto ocupado.

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s02>docker run -d -p 9090:80 --name sitio3 mi-sitio:v2
3e5753d4ca1734fb5b2ed7670bb872ac30da8ee8ee32df54a6ff63ea6c8be5e0

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio3 (335fbbddace0944b30421a97a2b44a09aa6b25444d75239fc94359e1757d1561): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information

C:\Users\Jimena\Documents\GitHub\sd-2027-1\entregas\valencia_jimena\s02>

El error anterior se produce porque el puerto 9090 ya estaba siendo utilizado y Docker no permite que dos contenedores utilicen simultaneamente un mismo puerto.

a) ¿Por qué no cambió la página sin reconstruir? 

Cambiar el archivo original después de ejecutar el comando docker build no modifica automáticamente la imagen, debido a que el contenedor utiliza la versión de index.html que se copió al momento de realizar el build. Por lo tanto, aunque se modifique el archivo original, el contenedor seguirá utilizando la versión anterior hasta que se vuelva a construir la imagen.


b) ¿Qué comparten y qué no dos contenedores de la misma imagen?

Ambos contenedores utilizan la misma imagen (tienen el mismo contenido y configuración). Sin embargo, cada uno funciona como una instancia diferente, ya que cada uno tiene nombre propio, proceso y puerto, al como se puede verificar al ejecutar el comando Docker ps en donde ambos contenedores aparecen listados. 

