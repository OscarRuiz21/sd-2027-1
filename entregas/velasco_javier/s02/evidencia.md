# Evidencia Javier Velasco· Sesión 02: Primer contenedor e imagen propia

## 1. Comandos utilizados
Lista de los comandos ejecutados 

```bash
#CHOQUE
docker run -d -p 8080:80 --name miweb nginx:alpine
docker run -d -p 8080:80 --name choque nginx:alpine

#MISION 2 Y 3
docker build -t mi-sitio:v2 .
docker stop sitio
docker rm sitio
docker run -d -p 9090:80 --name sitio-v2-1 mi-sitio:v2
docker run -d -p 9091:80 -error de puerto ocupad-name sitio-v2-2 mi-sitio:v2
docker ps
```
## 2. Docker PS
Dos contenedores de la imagen a la vez 

```text
CONTAINER ID   IMAGE          COMMAND                  CREATED         STATUS         PORTS                                           NAMES
c68ac4e531a3   mi-sitio:v2    "/docker-entrypoint.…"   2 seconds ago   Up 2 seconds   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp         sitio-v2-2
ebdfdcfd78a7   mi-sitio:v2    "/docker-entrypoint.…"   2 minutes ago   Up 2 minutes   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp         sitio-v2-1
58b25f3744a2   nginx:alpine   "/docker-entrypoint.…"   12 minutes ago  Up 12 minutes  0.0.0.0:8080->80/tcp, [::]:8080->80/tcp         miweb
8607b56fbf3a   nginx:alpine   "/docker-entrypoint.…"   15 minutes ago  Up 15 minutes  80/tcp                                          web2
6a0a95314afd   nginx:alpine   "/docker-entrypoint.…"   15 minutes ago  Up 14 minutes  80/tcp                                          web1
```

## 3. Error de puerto ocupado
```text
D:\GitHub\sd-2027-1\entregas\velasco_javier\s02>docker run -d -p 8080:80 --name choque nginx:alpine
6050b71f4f320252a45ac12354e54467c3ef99b44374d143b822312544e2549e

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint choque (3fbbdfc00d60b670fb8e8f45e87fd6581c7fcb0a7d58c8e601e0f98af95ce58e): Bind for 0.0.0.0:8080 failed: port is already allocated

Run 'docker run --help' for more information
```

## 4. ¿Por qué no cambió la página sin reconstruir?

Porque cuando le hicimos build, Docker tomó una copia de tu archivo HTML antiguo y creó la imagen. El contenedor vivo solo usa el archivo viejo, por lo que si editamos el archivo original no toma los cambios porque tiene la imagen del archivo antiguo.

## 5. ¿Qué comparten y qué no dos contenedores de la misma imagen?
Dos contenedores con la misma imagen comparten la misma base inicial, pero sus procesos, puertos y área de trabajo no son iguales.
