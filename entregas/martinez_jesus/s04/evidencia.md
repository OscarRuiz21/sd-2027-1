# Evidencia Práctica S04 - Docker, día dos: estado, red, configuración y compose
Jesús Martínez Trejo

## 1. Persistencia

**Sin volumen:**
```text
ERROR:  relation "cuentas" does not exist
LINE 1: SELECT * FROM cuentas;
                      ^
```

**Con volumen:**
```text
 id | saldo 
----+-------
  1 |   100
(1 row)

```

## 2. Redes 

**Prueba dentro de la red s04_default:**
```text
docker run --rm --network s04_default postgres:17-alpine pg_isready -h db
```
**Resultado de ejecución**
```text
db:5432 - accepting connections
```

**Prueba fuera de la red s04_default:**
```text
docker run --rm postgres:17-alpine pg_isready -h db
```
**Resultado de ejecución**
```text
db:5432 - no response
```

## 3. Configuración separada de la imagen

**Variables desde .env**
```text
docker run --rm --env-file .env postgres:17-alpine env | grep POSTGRES
```

**Resultado de ejecución**

```text
POSTGRES_PASSWORD=secreto
POSTGRES_DB=banco
```

**Variables desde .env.prod**
```text
docker run --rm --env-file .env.prod postgres:17-alpine env | grep POSTGRES
```

**Resultado de ejecución**
```text
POSTGRES_PASSWORD=Pr0dxD6_26
POSTGRES_USER=admin_banco
POSTGRES_DB=banco_prod
```

## Misión 4: Compose
**Contenedores corriendo**
```text
NAME           SERVICE   STATUS                 PORTS
s04-broker-1   broker    Up 4 hours             4369/tcp, 5671-5672/tcp, 15671/tcp, 15691-15692/tcp, 25672/tcp, 0.0.0.0:15672->15672/tcp, [::]:15672->15672/tcp
s04-cache-1    cache     Up 4 hours             6379/tcp
s04-db-1       db        Up 4 hours (healthy)   5432/tcp
s04-web-1      web       Up 4 hours             0.0.0.0:8080->80/tcp, [::]:8080->80/tcp

```

### Preguntas:

**¿Qué se pierde con `down` y qué con `down -v`?**
*   Con `docker compose down` se detienen y eliminan los contenedores y la red del proyecto, pero el volumen de almacenamiento externo se conserva. 
*   Con `docker compose down -v` también se destruyen los contenedores y la red,pero también se elimina de forma irreversible el volumen asociado, perdiendo todos los datos de la base de datos.

**¿Por qué web alcanza a db sin publicar el puerto 5432?**

Porque ambos servicios comparten la red virtual interna creada por Compose (`s04_default`). Dentro de ese entorno de Docker, todos los puertos están completamente expuestos entre sí y se pueden comunicar directamente usando sus nombres de servicio con el DNS interno. Publicar puertos solo es necesario para exponer el servicio hacia una red externa.

**¿Qué pasaría si el `.env` estuviera dentro de la imagen?**

Se violaría el principio de Separación de Configuración de The Twelve-Factor App. Al incluir variables de entorno dentro de la imagen, cualquier persona que tenga acceso a descargar podría inspeccionarla y extraer datos sensibles, comprometiendo la seguridad del sistema.
