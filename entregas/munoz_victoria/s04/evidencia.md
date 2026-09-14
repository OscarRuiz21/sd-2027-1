# Evidencia S04: Volúmenes, Redes, Env y Compose

## Misión 1: Persistencia (Volúmenes)
Demostramos qué pasa con los datos cuando borramos un contenedor con y sin volumen:
- **Sin volumen:** Al correr el contenedor de Postgres sin volumen, crear la tabla `cuentas` y meter el registro `1 | 100`, cuando borramos el contenedor con `docker rm -f` y lo volvemos a levantar con el mismo nombre, los datos desaparecen por completo y la base de datos nos bota el siguiente error al consultarla:
  ```text
  ERROR: relation "cuentas" does not exist
Con volumen nombrado: Al usar un volumen (-v datos_banco:/var/lib/postgresql/data), aunque eliminemos el contenedor con docker rm -f y lo recreemos desde cero, la información sobrevive y la consulta nos regresa intacto nuestro saldo:

Plaintext
 id | saldo
----+-------
  1 |   100
Misión 2: Redes (Discovery)
Comprobamos cómo se comunican los contenedores por su nombre dentro de una red:

Dentro de la red (--network redlab): Al lanzar un cliente alpine conectado a la misma red del servidor web, este resuelve el nombre y nos descarga el HTML directamente:

Plaintext
<!DOCTYPE html>
<html>
<head>
<title>Welcome to nginx!</title>
...
Fuera de la red (sin la bandera --network): Al intentar buscar al servidor web desde afuera, el sistema no sabe a dónde ir y falla con el error:

Plaintext
wget: bad address 'web'
Misión 3: Configuración (Variables de entorno)
Demostramos que una misma imagen puede comportarse diferente cambiando solo su configuración:

Levantamos dos contenedores (db_dev y db_prod) usando la misma imagen de Postgres pero cambiando las variables con -e:

Plaintext
POSTGRES_PASSWORD=dev
POSTGRES_DB=banco_dev

POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
Creamos nuestro archivo .env local con las credenciales reales (pepocumbia7) y lo protegimos agregándolo al .gitignore, subiendo únicamente el archivo .env.example como plantilla limpia para cualquier compañero.

Misión 4: Compose y Preguntas Teóricas
Salida de docker compose ps:
Plaintext
NAME           SERVICE   STATUS                   PORTS
s04-broker-1   broker    Up 9 seconds             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 9 seconds             6379/tcp
s04-db-1       db        Up 9 seconds (healthy)   5432/tcp
s04-web-1      web       Up 3 seconds             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
Preguntas teóricas:
¿Qué se pierde con down y qué con down -v?

Con down quitamos los contenedores y la red para limpiar la pantalla, pero los datos se salvan porque el volumen se queda guardado en la compu. Con down -v borramos absolutamente todo, incluyendo los volúmenes, por lo que perdemos la información para siempre.

¿Por qué web alcanza a db sin necesidad de publicar el puerto 5432?

Porque Docker crea una red privada y un directorio interno (como una agenda de contactos o DNS) donde cada contenedor se registra con su nombre. Se hablan por dentro de esa red sin necesidad de abrir puertas hacia afuera de la computadora.

¿Qué pasaría si el archivo .env estuviera horneado dentro de la imagen en lugar de venir de afuera?

Estaríamos pegando las contraseñas directo al código fijo. Si quisiéramos cambiar de opinión o pasar el sistema a producción, tendríamos que reconstruir toda la imagen desde cero cada vez, además de arriesgarnos a filtrar contraseñas si el código se vuelve público.
