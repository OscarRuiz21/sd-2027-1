# Evidencia - Reto Docker (Día Uno)

## Comandos principales utilizados
* `docker build -t mi-sitio:v2 .` : Para reconstruir la imagen con los nuevos cambios en el HTML.
* `docker stop sitio` y `docker rm sitio`: Para detener y borrar el contenedor viejo.
* `docker run -d -p 9090:80 --name sitio mi-sitio:v2`: Para levantar el contenedor actualizado.
* `docker run -d -p 9091:80 --name sitio2 mi-sitio:v2`: Para levantar una segunda instancia.

## Misión 3: Multiplícate (Contenedores corriendo)
[Aquí va el texto que te salió cuando hiciste `docker ps`]

## Misión 4: Error de puerto ocupado
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint sitio3... Bind for 0.0.0.0:9090 failed: port is already allocated

## Preguntas de la sesión

**¿Por qué no cambió la página sin reconstruir?**
Porque la imagen de Docker es inmutable y de solo lectura; el comando `build` congela los archivos tal y como estaban en ese momento. Para ver los cambios, es obligatorio reconstruir la imagen creando una nueva capa.

**¿Qué comparten y qué no dos contenedores de la misma imagen?**
Comparten la misma plantilla (la imagen base con el sistema y la aplicación). Lo que NO comparten son sus procesos ni su sistema de archivos en ejecución; cada contenedor es una instancia aislada e independiente.
