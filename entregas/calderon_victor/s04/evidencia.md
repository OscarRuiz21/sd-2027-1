
# Evidencia S04 · Docker, día 2

**Nombre:** Victor Emiliano Calderón Gutiérrez

## 1. Misión: Persistencia

### 1.1. Comandos ejecutado

```powershell
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker run -d --name db_reto -e POSTGRES_PASSWORD=secreto postgres:17-alpine
c850ed92b9cf54ede90d6f28a2aec0ce87796cd6bb2f900fd7a09e3ddfcee909
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "create table reto (id int primary key, completado bool);"
CREATE TABLE

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "insert into reto values(1, true);"                       
INSERT 0 1

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "select * from reto;"              
 id | completado 
----+------------
  1 | t
(1 row)

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker rm -f db_reto                                                        
db_reto
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker run -d --name db_reto -e POSTGRES_PASSWORD=secreto postgres:17-alpine                          
f58088784fc0ddd72f54563377b3e3402c03d929dea37695c33a7b37c1ee8ad1
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "select * from reto;"           
ERROR:  relation "reto" does not exist
LINE 1: select * from reto;
                      ^

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker rm -f db_reto                                                        
db_reto
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker run -d --name db_reto -v datos_reto:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
81112efdd97d0394e6ce6ac79f207ab1f906d9a76d504b52b7e25f56a9d48765
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "create table reto (id int primary key, completado bool);"             
CREATE TABLE

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "insert into reto values(1, true);"                                    
INSERT 0 1

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "select * from reto;"                                                  
 id | completado 
----+------------
  1 | t
(1 row)

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker rm -f db_reto                                                                                               
db_reto
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker run -d --name db_reto -v datos_reto:/var/lib/postgresql/data -e POSTGRES_PASSWORD=secreto postgres:17-alpine
73697065ff4432e151ead7f109fd8132dfa6dd7ddf9a44794a31dd08fab3b1d9
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec -it db_reto psql -U postgres -c "select * from reto;"                                                  
 id | completado 
----+------------
  1 | t
(1 row)

```

### 1.2. ¿Qué demuestra?

Tal como lo vimos en la clase, esto demuestra que los volúmenes son importantes cuando buscamos la persistencia de datos. Esta es una parte fundamental en datos, ya que si no tomamos las precauciones adecuadas, entonces no vamos a poder mantener esa información que podría ser relevante. Una de las cosas que para mi fueron muy importantes, es que debemos entender que este volumen es administrado por Docker, al menos si solo le ponemos nombre y no asignamos una ruta, por lo que este estado vive en los archivos de Docker.

## 2. Misión: Red

### 2.1. Contenido del compose.yaml

Para este ejercicio, decidí modificar un poco el compose para poder definir nuevos nombres y que esto quede un poco más personalizado a la actividad que estoy realizando. El contenido que se uso para el nuevo compose es el siguiente:

```yaml
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
    depends_on:
      db_reto:
        condition: service_healthy

  db_reto:
    image: postgres:17-alpine
    env_file: .env
    volumes:
      - datos_reto:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  cache:
    image: redis:8-alpine

  broker:
    image: rabbitmq:4-management-alpine
    ports:
      - "15672:15672"

volumes:
  datos_reto:
```

No se modificaron grandes cosas, solo fue el nombre del volumen. Y en el .env (que no se agrega por seguiradad) se añadieron nuevas credenciales.

### 2.2. Comandos ejecutados

```powershell
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker compose up -d                                                          
[+] up 6/6
 ✔ Network s04_default     Created                                                                                                                                                                                                                                0.0s
 ✔ Volume s04_datos_reto   Created                                                                                                                                                                                                                                0.0s
 ✔ Container s04-db_reto-1 Healthy                                                                                                                                                                                                                                5.8s
 ✔ Container s04-cache-1   Started                                                                                                                                                                                                                                0.3s
 ✔ Container s04-broker-1  Started                                                                                                                                                                                                                                0.2s
 ✔ Container s04-web-1     Started                                                                                                                                                                                                                                5.8s
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker run --rm --network s04_default postgres:17-alpine pg_isready -h db_reto
db_reto:5432 - accepting connections
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker run --rm postgres:17-alpine pg_isready -h db_reto                      
db_reto:5432 - no response
```

### 2.3. ¿Qué demuestra?

Para esta otra misión, pudimos demostrar nuevamente cómo es que funciona una red con Docker. Esto es mediante la configuración del DNS y de la red para que los contenedores puedan ser accedidos entre sí. Es importante entender que para probar la conexión se hizo uso del comando `pg_isready -h db_reto`, el cual tiene una funcionalidad muy similar a lo que se hace con `wget -qO-`, la gran diferencia es que `wget` funciona con protocolos HTTP, mientras que `pg_isready` sirve para revisar la conexión con bases de datos.

## 3. Misión: Configuración

### 3.1. Contenido del compose.yaml

Para esta otra misión, decidí trabajar con el compose nuevamente, ya que una vez que descubres su poder se simplifican un montón de cosas. Para poder trabajar con este tuve que asegurarme de crear primero los `.env` necesarios para poder traer sus propios secretos. Adicionalmente, se buscó cambiar el nombre de los contenedores a crear (para la bd) y por último modificar los volúmenes para cada uno de ellos. Por lo que el compose se ve a continuación.

```yaml
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
    depends_on:
      db_reto_dev:
        condition: service_healthy
      db_reto_prod:
        condition: service_healthy

  db_reto_dev:
    image: postgres:17-alpine
    env_file: .env
    volumes:
      - datos_reto_dev:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  db_reto_prod:
    image: postgres:17-alpine
    env_file: .env.prod
    volumes:
      - datos_reto_prod:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  cache:
    image: redis:8-alpine

  broker:
    image: rabbitmq:4-management-alpine
    ports:
      - "15672:15672"

volumes:
  datos_reto_dev:
  datos_reto_prod:
```

### 3.2. Comandos ejecutados

```powershell
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker compose up -d                                                     
[+] up 8/8
 ✔ Volume s04_datos_reto_dev    Created                                                                                                                                                                                                                           0.0s
 ✔ Volume s04_datos_reto_prod   Created                                                                                                                                                                                                                           0.0s
 ✔ Network s04_default          Created                                                                                                                                                                                                                           0.0s
 ✔ Container s04-db_reto_prod-1 Healthy                                                                                                                                                                                                                           5.8s
 ✔ Container s04-cache-1        Started                                                                                                                                                                                                                           0.3s
 ✔ Container s04-db_reto_dev-1  Healthy                                                                                                                                                                                                                           5.8s
 ✔ Container s04-broker-1       Started                                                                                                                                                                                                                           0.3s
 ✔ Container s04-web-1          Started                                                                                                                                                                                                                           5.8s
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec s04-db_reto_dev-1 env | Select-String "POSTGRES"

POSTGRES_PASSWORD=*******
POSTGRES_DB=***********
PGDATA=/var/lib/postgresql/data

PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker exec s04-db_reto_prod-1 env | Select-String "POSTGRES"

POSTGRES_DB=************
POSTGRES_PASSWORD=*******
PGDATA=/var/lib/postgresql/dat
```

NOTA: Se "censuran" las credenciales, tal como se mencionó en el laboratorio, para evitar que se filtren las mismas.

### 3.3. ¿Qué demuestra?

Para esta sección se tuvo un tema bastante interesante, ya que se trabajó con la parte de la configuración, y como se vio en clase, con el "Factor III". Este es importante, ya que con el auge del "vibe codging" cada vez son más comunes los casos en donde se hace push a un .env en producción, cosa que termina perjudicando la seguridad de la aplicación. De esta forma, podemos asegurarnos de que tenemos acceso a los secretos desde nuestro local, sin embargo, evitando que este quede expuesto en alguna parte de forma remota. 

## 4. Misión: Compose

### 4.1. Contenido del compose.yaml

Para esta última actividad, voy a retomar el compose que decidí crear en la misión 3. Por lo que podemos observar, es exactamente el mismo contenido, agregando lo que se pide e incluso un contenedor y un volumen adicional:

```yaml
services:
  web:
    image: nginx:alpine
    ports:
      - "8080:80"
    depends_on:
      db_reto_dev:
        condition: service_healthy
      db_reto_prod:
        condition: service_healthy

  db_reto_dev:
    image: postgres:17-alpine
    env_file: .env
    volumes:
      - datos_reto_dev:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  db_reto_prod:
    image: postgres:17-alpine
    env_file: .env.prod
    volumes:
      - datos_reto_prod:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  cache:
    image: redis:8-alpine

  broker:
    image: rabbitmq:4-management-alpine
    ports:
      - "15672:15672"

volumes:
  datos_reto_dev:
  datos_reto_prod:
```

### 4.2. Comandos ejecutados

```powershell
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker compose up -d
[+] up 8/8
 ✔ Network s04_default          Created                                                                                                                                                                                                                           0.0s
 ✔ Volume s04_datos_reto_dev    Created                                                                                                                                                                                                                           0.0s
 ✔ Volume s04_datos_reto_prod   Created                                                                                                                                                                                                                           0.0s
 ✔ Container s04-db_reto_dev-1  Healthy                                                                                                                                                                                                                           5.8s
 ✔ Container s04-db_reto_prod-1 Healthy                                                                                                                                                                                                                           5.8s
 ✔ Container s04-broker-1       Started                                                                                                                                                                                                                           0.3s
 ✔ Container s04-cache-1        Started                                                                                                                                                                                                                           0.3s
 ✔ Container s04-web-1          Started                                                                                                                                                                                                                           5.9s
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}"
NAME                 SERVICE        STATUS                   PORTS
s04-broker-1         broker         Up 9 seconds             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1          cache          Up 9 seconds             6379/tcp
s04-db_reto_dev-1    db_reto_dev    Up 9 seconds (healthy)   5432/tcp
s04-db_reto_prod-1   db_reto_prod   Up 9 seconds (healthy)   5432/tcp
s04-web-1            web            Up 3 seconds             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker volume ls                                                                   
DRIVER    VOLUME NAME
local     s04_datos_reto_dev
local     s04_datos_reto_prod
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker compose down                                                                
[+] down 6/6
 ✔ Container s04-web-1          Removed                                                                                                                                                                                                                           0.4s
 ✔ Container s04-cache-1        Removed                                                                                                                                                                                                                           0.3s
 ✔ Container s04-broker-1       Removed                                                                                                                                                                                                                           1.3s
 ✔ Container s04-db_reto_prod-1 Removed                                                                                                                                                                                                                           0.4s
 ✔ Container s04-db_reto_dev-1  Removed                                                                                                                                                                                                                           0.3s
 ✔ Network s04_default          Removed                                                                                                                                                                                                                           0.3s
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker volume ls                                                                   
DRIVER    VOLUME NAME
local     s04_datos_reto_dev
local     s04_datos_reto_prod
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker compose up -d                                                               
[+] up 6/6
 ✔ Network s04_default          Created                                                                                                                                                                                                                           0.0s
 ✔ Container s04-cache-1        Started                                                                                                                                                                                                                           0.3s
 ✔ Container s04-db_reto_dev-1  Healthy                                                                                                                                                                                                                           5.8s
 ✔ Container s04-broker-1       Started                                                                                                                                                                                                                           0.3s
 ✔ Container s04-db_reto_prod-1 Healthy                                                                                                                                                                                                                           5.8s
 ✔ Container s04-web-1          Started                                                                                                                                                                                                                           5.8s
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker compose down -v                                                             
[+] down 8/8
 ✔ Container s04-web-1          Removed                                                                                                                                                                                                                           0.4s
 ✔ Container s04-cache-1        Removed                                                                                                                                                                                                                           0.2s
 ✔ Container s04-broker-1       Removed                                                                                                                                                                                                                           1.4s
 ✔ Container s04-db_reto_prod-1 Removed                                                                                                                                                                                                                           0.3s
 ✔ Container s04-db_reto_dev-1  Removed                                                                                                                                                                                                                           0.3s
 ✔ Volume s04_datos_reto_prod   Removed                                                                                                                                                                                                                           0.0s
 ✔ Volume s04_datos_reto_dev    Removed                                                                                                                                                                                                                           0.0s
 ✔ Network s04_default          Removed                                                                                                                                                                                                                           0.2s
PS C:\Users\battl\Documents\sd-2027-1\entregas\calderon_victor\s04> docker volume ls      
DRIVER    VOLUME NAME
```

### 4.3. ¿Qué demuestra? Y preguntas adicionales

Para este primer punto, es importante explicar un poco lo que sucedió. Primero, se creó la imagen completa, en la cual pudimos ir confirmando todo lo que fuimos viendo en la práctica para el tema de configuración, lo cual se demostró mediante la ejecución de algunos comandos.

#### 4.3.1. ¿Qué se pierde con down y qué con down -v?

En los comandos ejecutados pudimos observar cómo se demuestra lo que se pierde con esa `-v`. Cuando hacemos el `down`, así sin nada más, lo único que estamos pidiéndole a Docker es que elimine lo que definimos en nuestra configuración de Docker en `compose.yaml`, pero conservando una parte muy importante, que son los volúmenes. Mientras tanto, al ejecutar `down -v`, le estamos pidiendo a Docker que no solo elimine la configuración hecha, sino también que se encargue de eliminar esos volúmenes. Al final pudimos confirmar esos datos corriendo el comando `docker volume ls`.

#### 4.3.2. ¿Por qué web alcanza a db sin publicar el puerto 5432?

Esto se explica por la configuración de la red. Cuando nosotros creamos un `compose.yaml`, Docker se encarga de configurar una red interna para los contenedores que estamos definiendo. Por eso, `web` puede comunicarse con `db` usando el nombre del servicio, sin que sea necesario publicar el puerto 5432 hacia nuestra máquina. La sección `ports` solo sería necesaria si quisiéramos acceder a PostgreSQL desde fuera de la red de Docker. Ahora la duda restante es: ¿por qué el 5432? Pues según una pequeña búsqueda que hice, esto es resultado de que el puerto por defecto para un servicio de Postgres es justamente ese; no es que nosotros lo decidamos en este ejercicio, sino que Postgres ya viene configurado para utilizarlo.

#### 4.3.3. ¿Qué pasaría si el .env estuviera dentro de la imagen?

Esto se relaciona por completo con lo que hemos visto del "Factor III" de la lectura de la semana, el cual dicta que la configuración no debe estar dentro de la imagen. Para entenderlo mejor, y no solo enunciando lo que representa ese factor, es que se busca dos cosas: seguridad y complejidad. Para la complejidad recordemos algo visto en el laboratorio anterior, si nosotros creamos una imagen y luego la modificamos, nuestro Docker no tiene la posibilidad de obtener esa imagen nuevo y luego reiniciar el Docker con esa nueva información, por lo que aquí entramos a que si se tiene dentro de la imagen, si existe alguna modificación en alguna variable, entonces tenemos que volver a reconstruir la imagen y luego modificar cada contenedor con esa nueva imagen. Mientras tanto, para la seguridad, es porque el tener una credencial expuesta en el contenedor lo puede hacer víctima de un ataque en la seguridad del mismo, si el contenedor es expuesto, entonces esas credenciales ahora estarán públicas, por lo que tenerlo fuera ayuda a que eso no pase.
