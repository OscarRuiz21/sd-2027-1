>[!Universidad Nacional Autónoma de México]
>Nombre: Christian Franco Ramírez
>Materia: Sistemas Distribuidos
>Semestre: 2027-1
>Grupo: 02
>Profesor: Oscar Manuel Ruiz Hurtado
## Misiones
### Mi-imagen

Haz tuya la página.. Tu `index.html` debe traer tu nombre y una tabla con los **5 comandos de hoy que más te sirvieron** y qué hace cada uno, **con tus palabras** (el repertorio del final es tu banco, pero copiarlo textual no cuenta).

*Revisar archivo `index.html`*
#### Comandos utilizados

| Comandos             | Descripción                                                                      |
| -------------------- | -------------------------------------------------------------------------------- |
| `docker pull`        | Descarga una imagen desde un registro a tu almacenamiento local.                 |
| `docker images`      | Lista todas las imágenes de Docker que ya tienes descargadas en tu sistema.      |
| `docker run`         | Crea y arranca un contenedor nuevo a partir de una imagen específica.            |
| `docker ps`          | Muestra una lista de los contenedores que están actualmente en ejecución.        |
| `docker stop <...>`  | Detiene el funcionamiento de un contenedor activo de forma segura, sin borrarlo. |
| `docker start <...>` | Vuelve a iniciar un contenedor que había sido detenido previamente.              |
| `docker exec`        | Ejecuta un comando adicional dentro de un contenedor que ya está corriendo       |
### `Docker ps`

```shell
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/Franco_Christ  
ian/s02$ docker run -d -p 9090:80 --name sitio mi-sitio  
docker: Error response from daemon: Conflict. The container name "/sitio" is already in use  
by container "488021dd9164c0fc3568a15a22216f5805a904d83c24c0073bdfd17375476d50". You have to  
remove (or rename) that container to be able to reuse that name.  
  
Run 'docker run --help' for more information
```

### Multiplicate

Se ejecutan simultamente dos contenendores a partir de la misma imagen `mi-sitio`, asignando un puerto diferente a cada uno `(9090 y 9091)`. Esto permite comprobar que una misma imagen puede utilizarse para crear múltiples contenedores independientes

```shell
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/Franco_Christ  
ian/s02$ docker rm -f sitio  
sitio  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/Franco_Christian/s02$ docker run -d -p 9090:80 --name sitio1 mi-sitio  
9a92c47cea5517b234b1b46866875044a46fe51e47f560b7d789ef3afab3ee66  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027 1/entregas/Franco_Christian/s02$ docker run -d -p 9091:80 --name sitio2 mi-sitio  
4f2f0f3084020fd9dbcf68fd1ee59ea1edb535db64a06727fbe9b3bb9a717734  
christian@pc-bda-cfr:~/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/Franco_Christian/s02$ docker ps  
CONTAINER ID   IMAGE          COMMAND                  CREATED          STATUS          PORT  
S                                     NAMES  
4f2f0f308402   mi-sitio       "/docker-entrypoint.…"   16 seconds ago   Up 14 seconds   0.0.  
0.0:9091->80/tcp, [::]:9091->80/tcp   sitio2  
9a92c47cea55   mi-sitio       "/docker-entrypoint.…"   26 seconds ago   Up 24 seconds   0.0.  
0.0:9090->80/tcp, [::]:9090->80/tcp   sitio1  
2830d3fe5a7f   nginx:alpine   "/docker-entrypoint.…"   9 hours ago      Up 9 hours      0.0.  
0.0:8080->80/tcp, [::]:8080->80/tcp   miweb  
59ca5f2c4181   nginx:alpine   "/docker-entrypoint.…"   9 hours ago      Up 9 hours      80/t  
cp                                    web2  
d7bf24acd72e   nginx:alpine   "/docker-entrypoint.…"   9 hours ago      Up 9 hours      80/t  
cp
```

### ¿Por qué no cambio la página sin reconstruir?
Los cambios que se reaizan dentro de `index.html`, no se reflejan automáticamente en un contenedor ya creado, esto debido a que Docker utiliza una imgane como plantilla para crear el contenedor. Al ejecutar `docker bulit`, los archivos actuales del proyecto se incorporan a la imagen en ese momento. 

Después, cuando se ejecuta `docker.run`, se crea un conntendor utilizando esa immagen, por lo que el contenedor tiene una copia de los archivos que existian durante la construcción. En caso de que docker se modifique, el archivo permanece sin cambios.

Por esto, para visualizar las modificaciones en la imagen actualizada utilizamos: `docker bulit -t mi-sitio . ` para posteriormente crear un nuevo contenedor a partir de la imagen actualizada.

Sin embargo, durante el desarrollo es posible utiizar volúmenens de Docker para montar los archivos locales dentro del contenedor. De esta manera, los cambios realizados en el equipo pueden reflejarse diectamente sin necesidad de reconstruir la imagen en cada modificación.
### ¿Qué comparten y qué no dos contenedores de la misma imagen?

Dos contenedores creados a partir de una misma imagen coparten la misma imagen base, parten del mismo  sistema de archivos, aplicaciones, configuraciones y archivos que fueron incluidos en la imagen al momento de contruirlo.

Sin embargo, los contenedores son intancias independientes. Por lo que cada uno posee su propia capa de escritura, por lo que los cambios realizados dentro de un contenedor no afectan al otro. 

Si se crean dos contenedores a partir de la imagen `mi-sitio`

```
docker run -d --name sitio1 mi-sitio 
docker run -d --name sitio2 mi-sitio
```
**Comparten:**
- La misma imagen de origen
- Los archivos y programas incluidos en dicha imagen
- La misma configuración definida por la imagen, salvo que se sobreescriba al ejecutar el contenedor. 

**No comparten:**
- Los cambios realizados denteo de cada contenedor
- Su capa de escritura
- Los procesos que se están ejecutando
- Su estado y archivos modificados dentro del contenedor
- Su nober y configuración de ejecución
