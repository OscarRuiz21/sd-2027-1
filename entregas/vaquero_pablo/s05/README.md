# S05 · Mexi Banco con Docker Compose

**Alumno:** Pablo Vaquero · Sistemas Distribuidos 2027-1 · Grupo 2

**Ejecución:** 26 de septiembre de 2026

**Entrega principal:** [reporte-s05.pdf](reporte-s05.pdf)

El reporte contiene observaciones de los seis pasos y capturas reales de Docker Desktop y Postman. Se usó Mexi Banco **v05.1**, commit `88203161aa31a371823715684a548d154e1516f8`, sin modificar su código.

## Resultados

- Ambos servicios (`app` y `db`) alcanzaron `healthy`.
- La colección oficial pasó **20 peticiones y 34 aserciones, sin fallos**.
- Transferencia interna de 200: origen 800 y destino 700.
- SPEI de 50 y reintento con la misma clave: mismo ID; origen 750; un solo movimiento SPEI.
- Errores esperados: 400, 404, 409 y 422. El saldo se mantuvo en 750.
- Después de `docker compose down` y un nuevo arranque: HTTP 200 y saldo 750.
- Después de `docker compose down -v` y un nuevo arranque: HTTP 404 para la misma cuenta.

La colección se ejecutó en su orden original mediante Newman, el runner de Postman. Las capturas de salud y persistencia se obtuvieron en Postman de escritorio. No se utilizó una cuenta de Postman: su cliente ligero permite consultas, pero solicita iniciar sesión para gestionar colecciones.

## Evidencia

- `evidencia/coleccion-oficial.json`: colección original del curso.
- `evidencia/resultado-postman.json`: exportación íntegra de la ejecución y sus aserciones.
- `evidencia/resultados-legibles.txt`: respuestas extraídas de esa exportación.
- `evidencia/contenedores.json`: salida de `docker compose ps --format json` antes de las pruebas de persistencia.
- `evidencia/01-postman-salud.png`: respuesta HTTP 200 / UP.
- `evidencia/02-docker-servicios.png`: aplicación y base de datos en Docker Desktop.
- `evidencia/03-saldo-antes.png`: cuenta con saldo 750 antes de reiniciar.
- `evidencia/04-despues-down.png`: la misma cuenta conserva su saldo.
- `evidencia/05-despues-down-v.png`: la misma consulta devuelve 404.

## Reproducción

```sh
git clone --branch v05.1 --depth 1 https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s05
cd mexi-banco-s05
docker compose up --build -d --wait
docker compose ps
```

Copiar la carpeta `evidencia` de esta entrega dentro de esa copia de Mexi Banco y ejecutar:

```sh
docker run --rm --network mexibanco \
  -v "$PWD/evidencia:/etc/newman" \
  postman/newman:alpine run coleccion-oficial.json \
  --env-var baseUrl=http://app:8080 \
  --reporters cli,json --reporter-json-export resultado-nuevo.json
```

En la ejecución entregada se usó la imagen Newman con digest `sha256:d9b5e780ead0026bbb37f3759cd7b10fc8a88cd4030c8266a7c5e343d75a5198`. El runner accede a `app:8080` dentro de Docker y Postman de escritorio a `localhost:8080` desde el host.

La colección genera nuevas CLABEs en cada corrida. Para repetir la prueba de persistencia, tomar la CLABE de origen de la nueva respuesta y consultar **esa misma** después de cada reinicio:

```sh
docker compose down
docker compose up -d --wait
# Consultar GET /cuentas/{clabe_de_esa_corrida}: debe conservar 750.

docker compose down -v
docker compose up -d --wait
# Consultar la misma CLABE: debe responder 404.
```

`down -v` es la parte del experimento que borra los datos del volumen de este proyecto. El SPEI es una simulación docente; no se conecta a un banco real.

## Fuentes

- [Guía oficial S05](https://oscarruiz21.github.io/sd-2027-1/labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Postman.html).
- [Código exacto de Mexi Banco v05.1](https://github.com/OscarRuiz21/mexi-banco/tree/88203161aa31a371823715684a548d154e1516f8).

Esta carpeta corresponde solamente al laboratorio S05.
