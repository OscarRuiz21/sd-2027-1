# S04: Docker, día dos

## Misión 1: persistencia

### Sin volumen nombrado: antes de recrear

```text
 id | saldo 
----+-------
  1 |   100
(1 row)

```

### Sin volumen nombrado: después de recrear

```text
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

### Con volumen datos_banco: antes de recrear

```text
 id | saldo 
----+-------
  1 |   100
(1 row)

```

### Con volumen datos_banco: después de recrear

```text
 id | saldo 
----+-------
  1 |   100
(1 row)

```

## Misión 3: configuración

Ambos contenedores usan postgres:17-alpine. Las contraseñas se muestran ocultas en esta evidencia.

### db_dev: archivo .env

```text
POSTGRES_PASSWORD=[OCULTA]
POSTGRES_DB=banco
```

### db_prod: archivo .env.prod

```text
POSTGRES_PASSWORD=[OCULTA]
POSTGRES_DB=banco_prod
```

## Misión 4: Compose

### Estado de los servicios

```text
NAME           SERVICE   STATUS                   PORTS
s04-broker-1   broker    Up 2 minutes             0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 2 minutes             6379/tcp
s04-db-1       db        Up 2 minutes (healthy)   5432/tcp
s04-web-1      web       Up 2 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

## Misión 2: red

### Dentro de s04_default

```text
db:5432 - accepting connections
```

### Fuera de s04_default

```text
db:5432 - no response
```

### Contenedores conectados a s04_default

```text
s04-cache-1 172.19.0.3/16
s04-db-1 172.19.0.2/16
s04-web-1 172.19.0.5/16
s04-broker-1 172.19.0.4/16

```

## Pruebas de apagado de Compose

### Consulta después de down y up

```text
 id | saldo 
----+-------
  1 |   100
(1 row)

```

### Consulta después de down -v y up

```text
ERROR:  relation "prueba_compose" does not exist
LINE 1: SELECT * FROM prueba_compose;
                      ^
```

## Respuestas de la misión 4

### ¿Qué se pierde con down y qué con down -v?

`docker compose down` elimina los contenedores y la red del proyecto. Se pierden los datos guardados únicamente en la capa de escritura de los contenedores, pero se conserva el volumen nombrado `s04_datos_banco`. En mi prueba, después de ejecutar `down` y `up`, la tabla `prueba_compose` conservó la fila con id 1 y saldo 100.

`docker compose down -v` también elimina los volúmenes del proyecto. Al volver a levantarlo, PostgreSQL creó nuevamente la base `banco` a partir de la configuración, pero la tabla `prueba_compose` ya no existía porque sus datos estaban en el volumen eliminado.

### ¿Por qué web alcanza a db sin publicar el puerto 5432?

Ambos servicios están conectados a la red `s04_default`. El DNS interno de Docker permite resolver el nombre `db` a su dirección IP y conectarse a `db:5432` dentro de esa red. Publicar el puerto sirve para permitir el acceso mediante un puerto de la máquina anfitriona; no es necesario para esta comunicación interna.

### ¿Qué pasaría si el .env estuviera dentro de la imagen?

La imagen incluiría la configuración y las contraseñas. Quien tuviera acceso a ella podría recuperar esas credenciales, y cambiar el archivo incorporado requeriría reconstruir la imagen. Mantener la configuración fuera permite usar la misma imagen con distintos archivos de entorno, como comprobé con `db_dev` y `db_prod`.
