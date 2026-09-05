\# Evidencias



\## 1. Comandos utilizados



\* `docker build -t mi-sitio .`

\* `docker run -d -p 9090:80 --name sitio mi-sitio`

\* `docker build -t mi-sitio:v2 .`

\* `docker stop sitio` y `docker rm sitio`

\* `docker run -d -p 9090:80 --name sitio1 mi-sitio:v2`

\* `docker run -d -p 9091:80 --name sitio2 mi-sitio:v2`

\* `docker ps`







\## 2. Salida de 'docker ps' con los dos contenedores



aleja@LAPTOP-SC2I79O0 MINGW64 \~/desktop/distribuidos/sd-2027-1/entregas/rodriguez\_alejandro/s02 (entregas\_rodriguez\_alejandro)

$ docker ps

CONTAINER ID   IMAGE         COMMAND                  CREATED          STATUS          PORTS                                     NAMES

7c53d2f2e98e   mi-sitio:v2   "/docker-entrypoint.…"   15 seconds ago   Up 15 seconds   0.0.0.0:9091->80/tcp, \[::]:9091->80/tcp   sitio2

e0c36220668f   mi-sitio:v2   "/docker-entrypoint.…"   29 seconds ago   Up 28 seconds   0.0.0.0:9090->80/tcp, \[::]:9090->80/tcp   sitio1







\## 3. Error de puerto ocupado



aleja@LAPTOP-SC2I79O0 MINGW64 \~/desktop/distribuidos/sd-2027-1/entregas/rodriguez\_alejandro/s02 (entregas\_rodriguez\_alejandro)

$ docker run -d -p 9090:80 --name choque mi-sitio

23a3d8ac572ebc35c04c91b662510ae6743599fdcc820f0b6a1124f78e99dc09



What's next:

&#x20;   Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (65abc1345668d3ebd95c956a77dcdd9950ad73773335adc40bb5e6fa864eccc9): Bind for 0.0.0.0:9090 failed: port is already allocated









\## 4. Preguntas

\*\*¿Por qué no cambió la página sin reconstruir?\*\*

Porque al hacer `build`, la imagen de Docker congeló mi archivo `index.html` original en una de sus capas de solo lectura. Editar el archivo en mi computadora no afecta al contenedor que ya está en ejecución porque está aislado; es obligatorio reconstruir la imagen para aplicar cambios.



\*\*¿Qué comparten y qué no dos contenedores de la misma imagen?\*\*

Comparten la imagen con el sistema operativo, dependencias y mi archivo html, es decir, comparten el kernel de mi computadora. No comparten su espacio de ejecución, cada contenedor tiene su proprio espacio con su propia red, memoria y procesos aislados, comportándose como entidades separadas

