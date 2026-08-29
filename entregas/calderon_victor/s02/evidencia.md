# Evidencia — Laboratorio S02: Docker, día uno

**Nombre: Calderón Gutiérrez Victor Emiliano**

## Comandos utilizados

### 1. Construir mi primera imagen

```text
docker build -t mi-sitio .
```

### 2. Ver las imágenes disponibles

```text
docker images

IMAGE                ID             DISK USAGE   CONTENT SIZE   EXTRA
alpine:latest        28bd5fe8b56d         13MB         3.93MB        
eclipse-temurin:21   85f00967bcc6        726MB          229MB        
hello-world:latest   5dd0d3e6e255       25.9kB         9.49kB        
mi-sitio:latest      44be356d6410       93.4MB         26.3MB    U   
nginx:alpine         db35bfc6b295       94.2MB         27.2MB    U   
```

### 3. Crear el primer contenedor

```text
docker run -d -p 9090:80 --name sitio mi-sitio
```

### 4. Reconstruir la imagen como versión 2

```text
docker build -t mi-sitio:v2 .
```

### 5. Detener el contenedor anterior

```text
docker stop sitio
docker rm sitio
```

### 6. Ejecutar dos contenedores de la versión 2

```text
docker run -d -p 9090:80 --name laboratorio1 mi-sitio:v2
docker run -d -p 9091:80 --name laboratorio2 mi-sitio:v2
```

### 7. Ver los contenedores en ejecución

```text
docker ps
```

## Salida de `docker ps`

```text
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s02> docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
68910d2933b0   mi-sitio:v2    "/docker-entrypoint.…"   33 minutes ago   Up 33 minutes   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   laboratorio2
05bd2474b2dd   mi-sitio:v2    "/docker-entrypoint.…"   33 minutes ago   Up 33 minutes   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   laboratorio1
12cd756fadd1   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours      0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
e4b7daa7133f   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours      80/tcp                                    web2
c295ed2b801f   nginx:alpine   "/docker-entrypoint.…"   2 hours ago      Up 2 hours      80/tcp                                    web1
```

## Error de puerto ocupado

```text
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s02> docker run --rm -d -p 9090:80 --name laboratorio_choque mi-sitio:v2
79a89a72385a205de3a5e0a12ecffb91ceba2d162dfa475f1d98a87473e3f0a8

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint laboratorio_choque (5910f01d401dc0c3fa224937f78261b5cfe01bf50afcbda84269775be9446aed): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information
```

## Preguntas del reto

### ¿Por qué no cambió la página sin reconstruir la imagen?

Esto yo lo entendí tal cual por el nombre "imagen", si nosotros creamos nuestra imagen con un docker build, tal cual se hace una imagen de los documentos que nosotros le pongamos en ese momento. Es decir, se le saca una copia a ese momento exacto, si nosotros posteriormente modificamos el archivo y abrimos nuestro localhost con la imagen, lo que vemos va a seguir siendo esa imagen con la que se construyo, es por eso que si hacemos modificaciones se debe volver a construir.

### ¿Qué comparten y qué no comparten dos contenedores de la misma imagen?

Recordemos el concepto anterior, de la imagen, recordemos que esta es una copia y que esta se puede reproducir varias veces, por lo tanto, los contenedores comparten justamente eso, la configuración de esa copia. Mientras tanto, al ser sistemas separados, cada uno tiene sus propios procesos, su propia red, sus propios estados, sus propios archivos. Es decir, son sistemas independientes (eso tienen distinto), mientras que esos contenedores tienen una imagen con la configuración del mismo (esta es lo que comparten).
