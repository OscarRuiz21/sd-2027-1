
# Evidencia S02 - Sanchez Mayen Tristan Qesen

## Comandos más usados
1. Lista de todos los contenedores

```bash
docker ps -a
```

2. Encender y detener un contenedor

```bash
docker start "nombre_contenedor"
docker stop "nombre_contenedor"
```

3. Construir la imagen del contenedor

```bash
docker build -t "nombre_imagen" .
```

4. Ejecutar el contenedor

```bash
docker run [parametros] "nombre_imagen"
```

5. Borrar un contenedor o imagen

```bash
docker rm "nombre_contenedor"
docker rmi "nombre_imagen"
```

## `docker ps` con dos contenedores de mi imagen

```text
CONTAINER ID   IMAGE         COMMAND                  CREATED          STATUS          PORTS                                     NAMES
4ea6e5f88661   mi-sitio:v2   "/docker-entrypoint.…"   12 minutes ago   Up 12 minutes   0.0.0.0:9091->80/tcp, [::]:9091->80/tcp   nostalgic_margulis
888fc4c5b540   mi-sitio      "/docker-entrypoint.…"   5 days ago       Up 26 minutes   0.0.0.0:9090->80/tcp, [::]:9090->80/tcp   sitio
```

## Error al tratar de iniciar otro contenedor con el mismo puerto

```text
docker run -d -p 9090:80 mi-sitio        
63a96fd50e2a28271aa0774a90a57085fd8a3df4754edde574173207a98c4505

What's next:
    Debug this container error with Gordon → docker ai "help me fix this container error"
docker: Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint quizzical_bartik (aa94c1d5233eaac65d6415292ac3d42964b79a7ceb6ae051150831c88e5925f0): Bind for 0.0.0.0:9090 failed: port is already allocated

Run 'docker run --help' for more information
```

## ¿Por qué no cambio la pagina?

El motivo por el cual no se actualiza el contenedor cuando se cambia el index es porque solo hacemos una copia de un momento exacto del index para el contenedor, si queremos que este se actualice debemos volver a crear la imagen y arrancar el contenedor de nuevo