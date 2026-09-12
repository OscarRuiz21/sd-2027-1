# Evidencia · Lab S04 · Docker, día dos

## Resumen de la sesión

**Qué hice:**
Desplegué varios contenedores conectados entre sí con Docker Compose, configurando persistencia con volúmenes nombrados, comunicación por nombre entre contenedores y configuración por variables de entorno. La misión que más me costó fue la 3 (configuración), por conflictos de nombres con contenedores que ya tenía y por verificar que cada `.env` se leyera bien.

**Qué gané y qué pagué:**
Gané persistencia que no depende del ciclo de vida del contenedor y poder levantar todo el sistema con un solo comando (`docker compose up -d`). Pagué la complejidad de manejar redes internas, healthchecks para el orden de arranque y tener cuidado de no subir credenciales al repo.

---

## Comandos principales que usé

- `docker run -v datos_banco:/var/lib/postgresql/data ...` — montar un volumen nombrado para que el estado viva fuera del contenedor.
- `docker run --network s04_default ...` — conectar un contenedor a la red compartida para que resuelva por nombre.
- `docker run --env-file .env ...` — inyectar variables de entorno desde un archivo externo (Factor III).
- `docker compose up -d` — crear red, volúmenes y levantar los servicios en segundo plano.
- `docker compose ps` — ver estado y healthcheck de los servicios.
- `docker compose down` / `down -v` — bajar el stack conservando o borrando los volúmenes.

---

## Misión 1: Persistencia

### 1. Sin volumen (los datos se pierden tras `docker rm -f`)

```text
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

### 2. Con volumen nombrado (los datos sobreviven tras `docker rm -f`)

```text
 id | saldo 
----+-------
  1 |   100
(1 row)
```

---

## Misión 2: Red y resolución de nombres

### 1. Dentro de la red compartida (`s04_default`)

```text
db:5432 - accepting connections
```

### 2. Fuera de la red (bridge por defecto)

```text
db:5432 - no response
```

---

## Misión 3: Configuración (Factor III)

### Contenedor de desarrollo (`--env-file .env`):

```text
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
```

### Contenedor de producción (`--env-file .env.prod`):

```text
POSTGRES_PASSWORD=prod
POSTGRES_DB=banco_prod
```

---

## Misión 4: Compose y preguntas

### Estado de los servicios (`docker compose ps`)

```text
NAME           SERVICE   STATUS                    PORTS
s04-broker-1   broker    Up 32 minutes             4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 32 minutes             6379/tcp
s04-db-1       db        Up 32 minutes (healthy)   5432/tcp
s04-web-1      web       Up 32 minutes             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
```

### Respuestas

**¿Qué se pierde con `down` y qué con `down -v`?**

`docker compose down` borra los contenedores y la red del proyecto, pero deja intacto el volumen `s04_datos_banco` con todos sus datos. `docker compose down -v` además borra los volúmenes, así que los datos de la base se pierden definitivamente.

**¿Por qué `web` alcanza a `db` sin publicar el 5432?**

Porque los dos están en la misma red interna que crea Compose (`s04_default`). Dentro de una red definida por el usuario, el DNS interno de Docker resuelve el nombre del servicio `db` a su IP, y todos los puertos quedan accesibles entre contenedores. El `ports:` solo hace falta para entrar desde la máquina host (por ejemplo, el navegador).

**¿Qué pasaría si el `.env` estuviera dentro de la imagen?**

Se rompería el Factor III de 12factor: la configuración quedaría acoplada al código. Cualquiera con acceso a la imagen podría sacar las credenciales, y para cambiar un valor entre entornos habría que reconstruir la imagen completa.