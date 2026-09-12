**Materia:** Sistemas Distribuidos
**Alumno:** Enrique Medrano Solano

## Comandos utilizados

```bash
docker build -t mi-sitio .
docker tag mi-sitio mi-sitio:v2
docker rmi mi-sitio:latest

docker run -d --name sitio -p 9090:80 mi-sitio:v2
docker run -d --name sitio2 -p 9091:80 mi-sitio:v2
docker rm -f sitio

docker ps

code index.html
code Dockerfile
```

## Salida de docker ps

```text
CONTAINER ID   IMAGE          COMMAND                  CREATED         STATUS         PORTS                                     NAMES
b69090b3d7c6   mi-sitio:v2    "/docker-entrypoint.…"   6 seconds ago   Up 5 seconds   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
5bdd9e753731   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago   Up 2 minutes   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
58c078d5ed49   nginx:alpine   "/docker-entrypoint.…"   4 hours ago     Up 4 hours     0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
624bbadefc69   nginx:alpine   "/docker-entrypoint.…"   4 hours ago     Up 4 hours     80/tcp                                    web2
263987c8bdfb   nginx:alpine   "/docker-entrypoint.…"   4 hours ago     Up 4 hours     80/tcp                                    web1
```

## Error de puerto ocupado

Para producir este error se levanta un contenedor en un puerto asignado en este caso en el 9090, lo que arroja este error:

```bash
enriq@QUIQUE_PC MINGW64 ~/Documents/UNAM/9no semestre/Sitemas Distribuidos/sd-2027-1/entregas/medrano/s02 (s02-medrano)
$ docker run -d --name sitio_error -p 9090:80 mi-sitio:v2
388db5e672312b172f9db78191be61ef01b99301fcf7f63b15a393a612d4d1e3
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio_error (82401bcf9dce8f20eb391df931bbad74c7d7495df0f6bef6d219eb7667d8f2dd): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information
```

## ¿por qué no cambió la página sin reconstruir?

Esto se debe a que Docker bloquea la imagen a la que se esta apuntando, ya que aparece que se esta utiliznado la imagen es por eso que se tiene que eliminar y volver a correrlo. 

## ¿qué comparten y qué no dos contenedores de la misma imagen?

Lo que comparten es la misma imagen, a su vez tambien se comparte el sistema operativo en este caso el instalado "Alpine Linux", al igual que las dependencias de servidor web "Nginx". Pero lo que no se comparte son los recursos del sitema, de manera que si uno es modificado el otro seguira igual ya que son completamente aislados, así como su IP.


