# Evidencia de Laboratorio S02 Docker

**Alumno: Echeverria Goicochea Erick Isaac**

## 1. Comando ejecutados

* `docker build -t mi-sitio:v1 .`
* `docker run -d -p 9090:80 --name sitio mi-sitio:v1`
* `docker build -t mi-sitio:v2 .`
* `docker stop sitio`
* `docker rm sitio`
* `docker run -d -p 9090:80 --name sitio mi-sitio:v2`
* `docker run -d -p 9091:80 --name sitio2 mi-sitio:v2`
* `docker run -d -p 9090:80 --name sitio3 mi-sitio:v2`

## 2. Salida de docker ps

```shell
──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s02]
└─$ docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                   NAMES
e0564a0c148f   mi-sitio:v2    "/docker-entrypoint.…"   5 seconds ago    Up 5 seconds    0.0.0.0:9091->80/tcp, :::9091->80/tcp   sitio2
bdd8bb747533   mi-sitio:v2    "/docker-entrypoint.…"   32 seconds ago   Up 31 seconds   0.0.0.0:9090->80/tcp, :::9090->80/tcp   sitio
5e756af036c3   nginx:alpine   "/docker-entrypoint.…"   42 minutes ago   Up 42 minutes   0.0.0.0:8080->80/tcp, :::8080->80/tcp   miweb
8246a9453624   7bc5ba2f958a   "/docker-entrypoint.…"   6 days ago       Up 48 minutes   80/tcp                                  web2
88acf7356ab2   7bc5ba2f958a   "/docker-entrypoint.…"   6 days ago       Up 47 minutes   80/tcp                                  web1
```

## 3. Error de puerto ocupado 

```shell
──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/s02]
└─$ docker run -d -p 9090:80 --name sitio3  mi-sitio:v2
826bfd5da9bfdccc79c9d9615afab14d5639c5808abee06694c884a039b650c8
docker: Error response from daemon: driver failed programming external connectivity on endpoint sitio3 (96db72b29e88bf551317ffea11aed8b83
2d75c8ebe97c8688830665a2f0eb69c): Bind for 0.0.0.0:9090 failed: port is already allocated.
```

## 4. Preguntas

* ¿Por qué no cambió la página sin reconstruir?
Porque las imágenes de Docker se congelan al momento de crearlas, por lo que la imagen guarda solo los archivos que se tenian  en ese instante y cuando modificamos el index.html,
el contenedor sigue corriendo la versión guardada en la imagen anterior. 
Para que el contenedor pueda ver los cambios, es necesario volver a construir la imagen y levantar un nuevo contenedor a partir de ella.

* ¿Qué comparten y qué no dos contenedores de la misma imagen?
Lo que comparten es la imagen, que incluye las librerías, los archivos originales y la configuración de la aplicación. 
Mientras que lo no comparte es su memoria,mientras que lo que no comparten es su memoria, sus procesos internos, sus puertos mapeados en la máquina y su capa de almacenamiento temporal. 
