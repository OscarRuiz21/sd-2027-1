**EVIDENCIA SANCHEZ XIMENA - 319659852   - 6 SEPT 26**





\------------ ***EJERCICIO 1*** ------------



Comandos utilizados

docker pull: Descarga una imagen pública desde Docker Hub hacia mi máquina local.

docker build: Construye una imagen personalizada leyendo el Dockerfile

docker run: Crea y pone a funcionar un contenedor nuevo basado en una imagen.

docker ps: Lista los contenedores activos en ejecución.

docker rm: Elimina un contenedor del sistema.





\------------ ***EJERCICIO 3*** ------------





CONTAINER ID   IMAGE          COMMAND                  CREATED             STATUS             PORTS                                     NAMES

a3e13f1c135b   mi-sitio:v2    "/docker-entrypoint.…"   8 seconds ago       Up 7 seconds       0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

9d7e78b6a2d3   mi-sitio:v2    "/docker-entrypoint.…"   5 minutes ago       Up 5 minutes       0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio

89d635743f48   nginx:alpine   "/docker-entrypoint.…"   40 minutes ago      Up 40 minutes      0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp   miweb

1cd129e95c68   nginx:alpine   "/docker-entrypoint.…"   About an hour ago   Up About an hour   80/tcp                                    web2

811f21162e24   nginx:alpine   "/docker-entrypoint.…"   About an hour ago   Up About an hour   80/tcp                                    web1







\------------ ***EJERCICIO 4*** ------------



¿Por qué no cambió la página sin reconstruir?

Porque las imágenes de Docker son inmutables. El comando docker build realiza una imagen estática del código en ese momento. Los cambios realizados en el archivo local index.html no se reflejan automáticamente en el contenedor a menos que se construya una nueva versión de la imagen (mi-sitio:v2) y se cree un nuevo contenedor a partir de ella.



¿Qué comparten y qué no dos contenedores de la misma imagen?

\-Lo que comparten: La imagen base (mi-sitio:v2), el sistema de archivos inicial, el código compilado (index.html) y las dependencias.

\- Lo que NO comparten: El entorno de ejecución, su estado en tiempo de ejecución, su ID/nombre único y el puerto de la máquina anfitriona asignado (9090 vs 9091).

