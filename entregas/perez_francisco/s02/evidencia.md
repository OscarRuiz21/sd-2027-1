\# Evidencia S02 - Docker

\# Perez Nava Francisco Javier



\## Comandos utilizados



docker pull nginx:alpine

docker images

docker run

docker ps

docker stop

docker start

docker exec

docker logs

docker build

docker system prune



\## Error de puerto ocupado



El error que obtuve al intentar utilizar un puerto que ya estaba ocupado:



C:\\Users\\franc\\Desktop\\Sistemas Distribuidos\\sd-2027-1\\entregas\\perez\_francisco\\s02>docker run -d -p 9090:80 --name choque2 mi-sitio:v2

c0b883b6160c8ed82a28b8e94cf7b2dfeac26ec1a9aec6af54e8332edf2331c8



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque2 (feb10947cf768e05ce8590f5e036ac017617e1c95998f26188f0cc353de4338a): Bind for 0.0.0.0:9090 failed: port is already allocated



Run 'docker run --help' for more information



\## Docker ps con dos contenedores



La salida de docker ps con los dos contenedores funcionando:



C:\\Users\\franc\\Desktop\\Sistemas Distribuidos\\sd-2027-1\\entregas\\perez\_francisco\\s02>docker ps

CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES

68eca791fce3   mi-sitio:v2    "/docker-entrypoint.…"   14 seconds ago   Up 14 seconds   0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

57e6dadf65a9   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago    Up 2 minutes    0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio

61e916f6b072   nginx:alpine   "/docker-entrypoint.…"   3 hours ago      Up 3 hours      0.0.0.0:8081->80/tcp, \[::]:8081->80/tcp   miweb

8d3a5a0283d7   nginx:alpine   "/docker-entrypoint.…"   3 hours ago      Up 3 hours      80/tcp                                    web2

4e66defe933c   nginx:alpine   "/docker-entrypoint.…"   3 hours ago      Up 3 hours      80/tcp                                    web1



\## Por que no cambio la pagina sin reconstruir



La pagina no cambio porque el archivo index.html ya habia quedado guardado dentro de la imagen cuando ejecute docker build. Aunque modifique el archivo en mi computadora, el contenedor seguia usando la imagen anterior. Despues para que fuera visible se reconstruyo la imagen, borrar el contenedor que existía y crear uno nuevo



\## Que comparten y que no dos contenedores de la misma imagen



Los dos contenedores comparten la misma imagen como base, por lo que parten de los mismos archivos y configuracion. Sin embargo, cada contenedor funciona de manera independiente, tiene sus propios procesos y puede ejecutarse usando un puerto diferente.

