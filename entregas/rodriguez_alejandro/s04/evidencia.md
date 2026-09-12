\# Evidencia LabS04



\## Misión 1: Persistencia

\*\*Sin volumen (El estado muere con el contenedor):\*\*

ERROR:  relation "cuentas" does not exist

LINE 1: SELECT \* FROM cuentas;



\*\*Con volumen (El estado sobrevive):\*\*

&#x20;id | saldo

\----+-------

&#x20; 1 |   100

(1 row)



\## Misión 2: Red

\*\*Dentro de la red (alcanzable por nombre):\*\*

db:5432 - accepting connections



\*\*Fuera de la red (falla):\*\*

db:5432 - no response



\## Misión 3: Configuración

La misma imagen, pero diferente configuración de entorno usando `--env-file`:

(db\_dev)

POSTGRES\_PASSWORD=dev

POSTGRES\_DB=banco\_dev



(db\_prod)

POSTGRES\_PASSWORD=prod

POSTGRES\_DB=banco\_prod



\## Misión 4: Compose y Teoría

\*\*Salida de docker compose ps:\*\*

NAME           IMAGE                          COMMAND                  SERVICE   CREATED         STATUS                   PORTS

s04-broker-1   rabbitmq:4-management-alpine   "docker-entrypoint.s…"   broker    3 minutes ago   Up 3 minutes             0.0.0.0:15672->15672/tcp, \[::]:15672->15672/tcp

s04-cache-1    redis:8-alpine                 "docker-entrypoint.s…"   cache     3 minutes ago   Up 3 minutes             6379/tcp

s04-db-1       postgres:17-alpine             "docker-entrypoint.s…"   db        3 minutes ago   Up 3 minutes (healthy)   5432/tcp

s04-web-1      nginx:alpine                   "/docker-entrypoint.…"   web       3 minutes ago   Up 2 minutes             0.0.0.0:8080->80/tcp, \[::]:8080->80/tcp



\*\*Preguntas teóricas:\*\*

\* \*\*¿Qué se pierde con `down` y qué con `down -v`?\*\*

&#x20; Con `down` se borran los contenedores y la red, pero se conserva el volumen intacto. Con `down -v` se borra todo, incluyendo los volúmenes, por lo que los datos se pierden permanentemente.

\* \*\*¿Por qué web alcanza a db sin publicar el puerto 5432?\*\*

&#x20; Porque Compose crea automáticamente una red interna (`s04\_default`) donde inyecta a todos los servicios. Docker usa un DNS interno que permite a los contenedores encontrarse entre sí usando su nombre de servicio, por lo que no hace falta publicar puertos hacia la máquina host para que se comuniquen internamente.

\* \*\*¿Qué pasaría si el `.env` estuviera dentro de la imagen?\*\*

&#x20; Se violaría el Factor III de la metodología y se perdería la capacidad de usar la misma imagen en distintos entornos sin reconstruirla, y tendríamos credenciales secretas expuestas de forma insegura en la imagen base.

