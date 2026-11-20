# S02 · Josué Hernández

## Comandos

```bash
docker build -t mi-sitio:v1 .
docker run -d -p 127.0.0.1:9090:80 --name sitio mi-sitio:v1
curl -fsS http://localhost:9090/
# Agregar la línea de la versión 2 al HTML local y volver a pedir la página.
curl -fsS http://localhost:9090/
docker build -t mi-sitio:v2 .
docker stop sitio
docker rm sitio
docker run -d -p 127.0.0.1:9090:80 --name sitio mi-sitio:v2
docker run -d -p 127.0.0.1:9091:80 --name sitio2 mi-sitio:v2
curl -fsS http://localhost:9090/
curl -fsS http://localhost:9091/
docker exec sitio ls /usr/share/nginx/html
docker logs sitio
```

## Dos contenedores activos

Los dos mostraron la versión 2 en los puertos 9090 y 9091.

```text
$ docker ps --filter 'name=^/sitio2?$'
CONTAINER ID   IMAGE         COMMAND                  CREATED         STATUS         PORTS                    NAMES
b6009b19640e   mi-sitio:v2   "/docker-entrypoint.…"   5 seconds ago   Up 4 seconds   127.0.0.1:9091->80/tcp   sitio2
400b55508575   mi-sitio:v2   "/docker-entrypoint.…"   5 seconds ago   Up 5 seconds   127.0.0.1:9090->80/tcp   sitio
```

## Puerto ocupado

Intenté usar el puerto 9090 para un tercer contenedor. Docker dio este error porque `sitio` ya usaba ese puerto.

```text
$ docker run --rm -d -p 127.0.0.1:9090:80 --name sitio-choque mi-sitio:v2
a55362bcafdc0ec81096f9d4678558aea4d00164df6b8ddfca0fbc839177bdaf
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio-choque (91f85fc26bb26d5fa44b74142f6f5ceaa6d9590dae9993e0bafe0f123091537b): Bind for 127.0.0.1:9090 failed: port is already allocated

Run 'docker run --help' for more information
```

## Preguntas

**¿Por qué no cambió la página al editar el HTML?** Docker Copia el archivo mientras construye la imagen y al guardar el HTML localmente el contenedor todavía entregaba el antiguo y la línea nueva apareció después de que la imagen y el contenedor fueran reconstruidos.

**¿Qué comparten los dos contenedores?** Todos provienen de la misma imagen lo que significa que todos tienen el mismo nginx y el mismo HTML y todos funcionan por separado, mantienen sus propios registros y usan diferentes puertos.
