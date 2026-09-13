# Evidencia Sesion 04 - Reto 
 
## Mision 1: Persistencia 
```text 
CON VOLUMEN (Sobrevive): 
id 
---- 
   1 
(1 row) 
 
SIN VOLUMEN (No sobrevive): 
ERROR:  relation "prueba2" does not exist 
LINE 1: SELECT * FROM prueba2; 
``` 
 
## Mision 2: Red 
```text 
DENTRO DE LA RED (docker run --rm --network s04_default postgres:17-alpine pg_isready -h db): 
db:5432 - accepting connections 
 
FUERA DE LA RED (docker run --rm postgres:17-alpine pg_isready -h db): 
db:5432 - no response 
``` 
 
## Mision 3: Configuracion 
```text 
db_dev env: 
POSTGRES_DB=banco 
POSTGRES_PASSWORD=secreto 
 
db_prod env: 
POSTGRES_DB=banco_produccion 
POSTGRES_PASSWORD=password_super_seguro 
``` 
 
## Mision 4: Compose 
### Respuestas a las preguntas: 
- **¿Que se pierde con `docker compose down` y que con `docker compose down -v`?** 
  `down` detiene y elimina contenedores, redes y elementos temporales. `down -v` elimina ademas los volumenes nombrados asociados, destruyendo los datos persistidos. 
- **¿Por que `web` alcanza a `db` sin publicar el puerto 5432?** 
  Porque se comunican a traves de la red interna de Docker (`s04_default`) usando resolucion DNS por nombre de servicio. La publicacion de puertos (`ports`) solo se requiere para exponer servicios hacia la maquina host externa. 
- **¿Que pasaria si el `.env` estuviera dentro de la imagen?** 
  Se perderia la flexibilidad del Factor III de los 12-Factor Apps (configuracion acoplada al codigo/imagen) y se expondrian credenciales e informacion sensible en el repositorio de imagenes (Container Registry). 
