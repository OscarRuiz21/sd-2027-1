Contreras del Angel Diego Adrian - 320298835



1. comandos que utilicé 



docker build -t mi-sitio

docker run -d -p 9090:80 --name choque nginx:alpine

docker rm -f

docker ps



2\. docker ps



PS C:\\Users\\dieg7\\sd-2027-1\\entregas\\contreras\_diego\\s02> docker ps

CONTAINER ID   IMAGE          COMMAND                  CREATED              STATUS              PORTS                                     NAMES

683c5fe5d3ca   mi-sitio:v2    "/docker-entrypoint.…"   27 seconds ago       Up 27 seconds       0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

a0e22e118db7   mi-sitio:v2    "/docker-entrypoint.…"   About a minute ago   Up About a minute   0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio

34eb9e9c1eed   nginx:alpine   "/docker-entrypoint.…"   34 minutes ago       Up 34 minutes       0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp   miweb

cb71dde2a923   nginx:alpine   "/docker-entrypoint.…"   40 minutes ago       Up 40 minutes       80/tcp                                    web2

3f44a7d81c94   nginx:alpine   "/docker-entrypoint.…"   41 minutes ago       Up 36 minutes       80/tcp                                    web1

PS C:\\Users\\dieg7\\sd-2027-1\\entregas\\contreras\_diego\\s02>



3\. error



PS C:\\Users\\dieg7\\sd-2027-1\\entregas\\contreras\_diego\\s02> docker run -d -p 9090:80 --name error mi-sitio:v2

caf9df401462b24721b84b418f206fbb1879be5e886784e6d8528d2474cccb45



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint error (6354e4532143504b5f2f87d4d80ad2044291b1dd5061480788bde07be75b7e0f): Bind for 0.0.0.0:9090 failed: port is already allocated



Run 'docker run --help' for more information

PS C:\\Users\\dieg7\\sd-2027-1\\entregas\\contreras\_diego\\s02>



4\. preguntas



¿por qué no cambió la página sin reconstruir?

debido a que las imágenes no cambian una vez ya creadas, se tienen que actualizar 



¿qué comparten y qué no los dos contenedores de la misma imagen?

comparten todos los mismos archivos y no comparten su tiempo de ejecución





