# Práctica de Git y Docker

## Comandos de Git

```bash
git clone https://github.com/OscarRuiz21/sd-2027-1.git
cd sd-2027-1
git checkout -b s02-arroyo
mkdir -p entregas/arroyo_leonardo/s02
cd entregas/arroyo_leonardo/s02
git status
git add entregas/arroyo_leonardo/s02
git commit -m "S02: primer contenedor e imagen propia"
git push -u origin s02-arroyo
```

---

## Comandos de Docker

```bash
docker --version
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
docker run -d -p 8080:80 --name miweb nginx:alpine
docker exec -it miweb sh
docker logs miweb
docker build -t mi-sitio .
docker run -d -p 9080:80 --name sitio mi-sitio   # Error: aquí me equivoqué y usé 9080 en vez de 9090
docker run -d -p 9090:80 --name sitio1 mi-sitio
docker run -d -p 9090:80 --name sitio2 mi-sitio
docker rm sitio
```

---

## Comandos dentro del contenedor (terminal)

```bash
cat /etc/os-release
ps aux
exit
ls /usr/share/nginx/html
```

---

## Comandos más útiles para esta práctica

| Comando | Descripción |
|---------|-------------|
| `docker pull` | Descarga imágenes desde Docker Hub |
| `docker run` | Crea y ejecuta un contenedor desde una imagen |
| `docker ps` | Muestra contenedores activos (con `-a` también los detenidos) |
| `docker stop` | Detiene un contenedor sin eliminarlo |
| `docker build` | Construye una imagen personalizada desde un Dockerfile |

---

## Salida del comando `docker ps`

```
PS C:\Users\arroy\Documents\9no semestre\SD\sd-2027-1\entregas\arroyo_leonardo\s02> docker ps
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORTS                                     NAMES
343eb08639d8   mi-sitio:v2    "/docker-entrypoint.…"   5 seconds ago    Up 5 seconds    0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
fed0cb0dd280   mi-sitio:v2    "/docker-entrypoint.…"   10 seconds ago   Up 9 seconds    0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio1
fdc112cad53c   mi-sitio:v2    "/docker-entrypoint.…"   52 seconds ago   Up 51 seconds   0.0.0.0:9080->80/tcp, [::]:9080->80/tcp   sitio
69144f466595   nginx:alpine   "/docker-entrypoint.…"   23 minutes ago   Up 23 minutes   0.0.0.0:8080->80/tcp, [::]:8080->80/tcp   miweb
ba7235c7688b   nginx:alpine   "/docker-entrypoint.…"   30 minutes ago   Up 30 minutes   80/tcp                                    web2
009561cff5a7   nginx:alpine   "/docker-entrypoint.…"   30 minutes ago   Up 30 minutes   80/tcp
```

---

## Preguntas

### ¿Por qué no cambió la página sin reconstruir?

Al modificar el archivo `index.html`, este cambio no afecta a la imagen ya creada. Durante la construcción de la imagen, se genera una copia "instantánea" del contenido en ese momento. Cada contenedor ejecuta una copia independiente de esa imagen. Por lo tanto, para reflejar cambios en el contenido, es necesario reconstruir la imagen y volver a crear los contenedores, esa es la razón por la cual, al cambiar el index.html sin reconstruir no se ve reflejado en el `localhost:9090`.

### ¿Qué comparten y qué no dos contenedores de la misma imagen?

- **Qué comparten:**
  - Archivos base de la imagen.
  - Recursos del sistema operativo del host (kernel, memoria, CPU).

- **Qué NO comparten:**
  - Estado de ejecución (procesos, variables de entorno en memoria).
  - Red (cada contenedor tiene su propia IP y puertos).
  - Datos modificados en tiempo de ejecución.

---

## Error por puerto en uso

```bash
PS C:\Users\arroy\Documents\9no semestre\SD\sd-2027-1\entregas\arroyo_leonardo\s02> docker run -d -p 9091:80 --name sitio3 mi-sitio:v2
d4572b73a6634c0291c2a936a07a04a7ae6b0b434e7825a7770e38225416ef1b
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio3 (61eb9d447be93a63697890c78b19c03a3d7dab056763a10e94a07c6130de78a5): Bind for 0.0.0.0:9091 failed: port is already allocated
```

**Solución:** El puerto `9091` ya estaba en uso por otro contenedor. Para solucionarlo, se debe detener o eliminar el contenedor que lo ocupa, o bien mapear a otro puerto disponible.

```