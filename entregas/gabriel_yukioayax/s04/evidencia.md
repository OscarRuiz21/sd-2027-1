# Evidencia S04

## Misión 1: Persistencia
**Sin volumen:**
`ERROR:  relation "cuentas" does not exist`
`LINE 1: SELECT * FROM cuentas;`

**Con volumen (la fila sobrevive al rm -f):**
` id | saldo `
`----+-------`
`  1 |   100`
`(1 row)`

## Misión 2: Red
**Dentro de la red (alcanza por nombre):**
`db:5432 - accepting connections`

**Fuera de la red:**
`db:5432 - no response`

## Misión 3: Configuración
**Salida de env en db_dev:**
`POSTGRES_PASSWORD=dev`
`POSTGRES_DB=banco_dev`

**Salida de env en db_prod:**
`POSTGRES_PASSWORD=prod`
`POSTGRES_DB=banco_prod`

*   **`.gitignore`**: Evita que subamos nuestras credenciales reales (el `.env`) al repositorio, manteniendo la seguridad.
*   **`.env.example`**: Sirve como plantilla para que cualquier miembro del equipo sepa qué variables de entorno necesita configurar localmente para que el proyecto funcione.

## Misión 4: Compose
*   **`docker compose down`**: Borra contenedores y redes del proyecto, pero **conserva** los volúmenes (los datos sobreviven).
*   **`docker compose down -v`**: Borra todo, **incluyendo los volúmenes** (los datos se pierden).
*   **¿Por qué web alcanza a db sin publicar puerto?**: Porque dentro de la red que crea Docker Compose, todos los contenedores tienen acceso a todos los puertos de los demás usando el nombre del servicio como DNS interno. El mapeo de puertos (`ports:`) solo es para exponerlos hacia tu máquina local (host).
*   **¿Qué pasaría si el .env estuviera dentro de la imagen?**: Violaríamos el principio de "configuración en el entorno" (Factor III). Si las credenciales o nombres de base de datos están quemados en la imagen, tendríamos que reconstruir una imagen distinta para cada entorno (desarrollo, pruebas, producción), y las contraseñas quedarían expuestas a quien tenga la imagen.