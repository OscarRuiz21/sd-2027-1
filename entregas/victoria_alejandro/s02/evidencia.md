###Comandos utilizados###
docker pull nginx:alpine
docker images
docker run -d --name web1 nginx:alpine         
docker run -d --name web2 nginx:alpine   
docker ps
docker stop web1 
docker ps -a 
docker start web1 
docker run -it --rm alpine sh 
docker run --rm -it eclipse-temurin:21 jshell   
docker run -d -p 8080:80 --name miwebVDA nginx:alpine
docker run -d -p 8080:80 --name choqueVDA nginx:alpine
docker rm choqueVDA
docker exec -it miwebVDA sh 
docker logs miwebVDA
docker build -t mi_sitio_vda .
docker run -d -p 9090:80 --name sitio mi_sitio_vda 
docker build -t mi_sitio_vda:v2 . 
docker stop sitio
docker rm sitio
docker run -d -p 9090:80 --name sitio9090 mi_sitio_vda:v2
docker run -d -p 9091:80 --name sitio9091 mi_sitio_vda:v2
docker run -d -p 9091:80 --name sitio9091_error mi_sitio_vda:v2

###Salida docker ps###
CONTAINER ID   IMAGE             COMMAND                  CREATED              STATUS              PORTS                  NAMES
bac41d48571c   mi_sitio_vda:v2   "/docker-entrypoint.…"   44 seconds ago       Up 43 seconds       0.0.0.0:9091->80/tcp   sitio9091
136dd85b4061   mi_sitio_vda:v2   "/docker-entrypoint.…"   About a minute ago   Up About a minute   0.0.0.0:9090->80/tcp   sitio9090
e32f92370e44   nginx:alpine      "/docker-entrypoint.…"   51 minutes ago       Up 51 minutes       0.0.0.0:8080->80/tcp   miwebVDA
7da195df7dc3   nginx:alpine      "/docker-entrypoint.…"   About an hour ago    Up About an hour    80/tcp                 web2
910d32310903   nginx:alpine      "/docker-entrypoint.…"   About an hour ago    Up 58 minutes       80/tcp                 web1

###Error###
b2f8dcaefb5a66505e763a3ad43803d94dec4e57ee4c30075d995b10f6a2ece9
docker: Error response from daemon: driver failed programming external connectivity on endpoint sitio9091_error (32ef50feb97cd654f17304c9da6f11b9c185e3d575158745e7316eaaba3d82ad): Bind for 0.0.0.0:9091 failed: port is already allocated.

###¿Por qué no cambió la página sin reconstruir?###
Porque cuando se creó la imagen se utilizó el archivo index.html que se tenía en ese momento. Las imágenes no se actualizan solas ni mantienen ninguna conexión con el directorio que los originó. La solución es crear una nueva versión de la imagen.

###¿Qué comparten y qué no dos contenedores de la misma imagen?###
Dos contenedores con la misma imagen tienen los mismos archivos que había en el directorio en el que se creó la imagen. Varía la configuración y los parámetros con los que se crean los contenedores. Por ejemplo el nombre, los puertos abiertos de la máquina anfitriona y el modo en que se ejecuta el contenedor.