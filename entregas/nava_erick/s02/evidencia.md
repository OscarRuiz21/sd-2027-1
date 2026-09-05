Al realizar modificaciones en el archivo `index.html` no se verán los cambios en el contenedor ya levantado porque la construcción del entorno involucró la versión anterior y no la nueva. Es por eso que no se muestran los cambios futuros, puesto que dentro del contenedor está el `index` con la versión que carga; si se editara esta, sí se mostrarían los cambios.

Aquí se puede ver el error al intentar utilizar el mismo puerto. Básicamente sucede porque el puerto ya está en uso:

```bash
eri@saiko:~/Documents/sistemasDistribuidos/sd-2027-1/entregas/nava_erick/s02$ docker run -d -p 9091:80 --name sitio2 mi-sitio:v2
docker: Error response from daemon: Conflict. The container name "/sitio2" is already in use by container "1891d5c4ce533979e10c68d09023aaeeadf58bb4a9c057297f95bd0ccce0e12e". You have to remove (or rename) that container to be able to reuse that name.

Run 'docker run --help' for more information

eri@saiko:~/Documents/sistemasDistribuidos/sd-2027-1/entregas/nava_erick/s02$ docker run -d -p 9091:80 --name sitio3 mi-sitio:v2
954d5482c9b98c20a8086a6d0d755292eb3ac924f09ed337a243d9de93d87992
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio3 (3f161d50e2ce77305469c415247b8361094203014c2295d927a3b1b4413d68eb): Bind for 0.0.0.0:9091 failed: port is already allocated

Run 'docker run --help' for more information
```

Ejecutando el comando `docker ps` se puede ver que se tienen los contenedores levantados:

```bash
docker ps
CONTAINER ID   IMAGE         COMMAND                  CREATED          STATUS          PORTS                                         NAMES
1891d5c4ce53   mi-sitio:v2   "/docker-entrypoint.…"   12 seconds ago   Up 12 seconds   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp       sitio2
7875b68d3b94   mi-sitio:v2   "/docker-entrypoint.…"   22 seconds ago   Up 22 seconds   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp       sitio
```

**Contestando a: ¿por qué no cambió la página sin reconstruir? · ¿qué comparten y qué no dos contenedores de la misma imagen?**

Comparten la versión del archivo con el que se creó el *build*. Este archivo vive dentro de la imagen, al momento de modificar el archivo fuera del contenedor no se verán los cambios en ninguno de los dos. Pero si en mi contenedor 1 modifico su archivo interno sí tendrá cambios, mientras que, para un contenedor 2 creado con la misma imagen no se verán los cambios porque su archivo `index` es propio y está aislado del sistema y del otro contenedor. Siendo así que se puede visualizar el propósito de Docker, que es el aislamiento de un entorno replicable.