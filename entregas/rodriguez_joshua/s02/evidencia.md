----
# Materia: Sistemas Distribuidos
# Alumno: Joshua Rodríguez Zamora
----
---
### Comandos utilizados
---
```bash
docker pull nginx:alpine 
docker build -t mi-sitio-v2
docker images
docker run -d -p 9090:80 --name sitio mi-sitio-v2
docker run -d -p 9091:80 --name sitio2 mi-sitio-v2
docker ps
```
---
### Docker ps 
---
```bash
$ docker ps
CONTAINER ID   IMAGE         COMMAND                  CREATED          STATUS          PORTS            NAMES
8956552846bf   mi-sitio-v2   "/docker-entrypoint.…"   45 seconds ago   Up 44 seconds   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
9a53ba54ff33   mi-sitio-v2   "/docker-entrypoint.…"   17 minutes ago   Up 2 seconds    0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2
```

---
### Error generado de puerto ocupado
---
```bash
$ docker run -d -p 9090:80 --name mi-sitio-error mi-sitio-v2
74fa7d3d84b133a864ad38edfcd192572308e4e9c45c73972afbdbd50dcd5fee

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint keen_carver (01bb11220153f5f025fffe8f8e7da5c8c98fac08d1e0b6c40a8a56679771a62c): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information
```
---
### ¿Por qué no cambió la página sin reconstruir? 
---
Esto se debe a que las imágenes se quedan guardadas en el contenedor tal y como se construyen, esto debido a su inmutabilidad.
Por lo que, de hacerse un cambio, estas no cambiarán hasta que se actualice la imagen y en base a esta se reconstruya el contenedor.

---
### ¿Qué comparten y qué no dos contenedores de la misma imagen?
---
| Que comparten | Que NO comparten |
| :---: | :---: |
| Tanto la imagen como el sistema operativo a partir de alpine | El puerto, ya que sitio y sitio2 usan el 9090 y 9091 respectivamente |
| Los archivos base, para levantar el sistema | El ciclo de vida, son independientes y tienen distintos procesos en ejecución |
