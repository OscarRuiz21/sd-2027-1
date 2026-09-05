# Evidencia de Práctica

## Comandos utilizados

```bash
docker pull nginx:alpine
docker images
docker run -d --name web1 nginx:alpine
docker run -d --name web2 nginx:alpine
docker ps
docker stop web1
docker ps -a       # …aquí web1 aparece como "Exited (0)"
docker start web1  # y revive
docker run -it --rm alpine sh # ya estás DENTRO del contenedor:
cat /etc/os-release   # Alpine Linux, no tu sistema
ps aux                # cuenta los procesos…
exit
docker run -d -p 8080:80 --name miweb nginx:alpine
docker run -d -p 8080:80 --name choque nginx:alpine
docker exec -it miweb sh
ls /usr/share/nginx/html      # ahí vive la página que viste
docker logs miweb # lo que el servicio ha escrito en su salida
docker build -t mi-sitio .
docker build -t mi-sitio:v2 .
docker run -d -p 9090:80 --name sitio mi-sitio
docker run -d -p 9090:80 --name sitio1 mi-sitio:v2
docker run -d -p 9091:80 --name sitio2 mi-sitio:v2

```

### Error presentado

```powershell
PS C:\Users\PC\Desktop\Sistemas Distribuidos\sd-2027-1\entregas\miyasaki_yuichi\s02> docker run -d -p 9090:80 --name sitio2 mi-sitio:v2

844124d29827d4757df98b00fea1181df740f8461ef83fc677fbef1b4e857dcb

What's next:
  Debug this container error with Gordon → docker ai "help me fix this container error"

docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio2 (2a17435d2e49d48ac81b373e93641360cee360c1f038c194835c3d19fe64ab9c): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information
```

## Respuestas

### ¿Por qué no cambió la página sin reconstruir?

Porque la instrucción `COPY` del `Dockerfile` empaqueta una copia estática de los archivos locales al momento de construir la imagen (`docker build`).

### ¿Qué comparten y qué no dos contenedores de la misma imagen?

* **Lo que comparten:**

  * Las capas base de la imagen.
  * El kernel del sistema operativo host.

* **Lo que no comparten:**

  * Su propia capa de lectura y escritura.
  * El espacio de nombres de red, su IP interna dentro de la red Docker y el puerto mapeado en el host (por ejemplo, uno usa el 9090 y el otro el 9091).