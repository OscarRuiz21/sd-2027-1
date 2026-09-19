# Lab 05 · Mexi Banco con Compose, con sus manos (SD · S05 · 2026-09-19)

## Objetivo
Al terminar puedes levantar un sistema de varios contenedores con un solo comando, y explicar
(porque lo viste, no porque lo leíste) qué resuelve Docker Compose y qué no.

## Antes de empezar
- Docker Desktop corriendo (`docker run hello-world` sin errores). Ya lo tienes desde la S01.
- Baja las imágenes antes de venir a clase, con buena red: es lo que más tarda.
  ```
  docker pull postgres:17-alpine
  docker pull eclipse-temurin:21-jdk-alpine
  docker pull eclipse-temurin:21-jre-alpine
  ```
- Clona Mexi Banco en la etiqueta de esta sesión (`v05.1`):
  ```
  git clone --branch v05.1 https://github.com/OscarRuiz21/mexi-banco.git
  cd mexi-banco
  ```
- Verifica que arranca: `docker compose up --build`. La primera vez tarda unos minutos (construye
  la imagen); las siguientes es casi instantáneo.
- Si prefieres Postman a `curl`, la guía visual [`Lab-S05-Mexi-Banco-Postman.html`](Lab-S05-Mexi-Banco-Postman.html)
  recorre lo mismo petición por petición, con la colección
  [`mexi-banco-v05.1.postman_collection.json`](mexi-banco-v05.1.postman_collection.json) y 34 pruebas
  automáticas. Es lo que se mostró en la clase del 2026-09-19. Usa Postman de escritorio: la versión
  web no alcanza tu `localhost`.

## Contexto
Mexi Banco es el banco digital ficticio del que hemos hablado desde la S01: cuenta, movimiento,
transferencia, notificación, y ahora también SPEI. Hasta hoy solo lo habíamos usado como ejemplo
hablado. A partir de esta sesión también es código que corre, y lo van a operar ustedes mismos.
Es un solo monolito. Con el tiempo se va a ir partiendo en piezas, pero eso no toca hoy.

## Parte 1 · Arriba y respondiendo (≈20 min)
1. `docker compose up --build`.
2. `docker compose ps`: los dos servicios (`db`, `app`) deben decir `healthy`, no solo `running`.
   Si `app` no llega a `healthy`, revisa los logs con `docker compose logs app`.
3. Abre una cuenta:
   ```
   curl -X POST localhost:8080/cuentas -H "Content-Type: application/json" \
     -d '{"clabe":"002180000000000001","titular":"Tu nombre","saldoInicial":1000}'
   ```
4. Consúltala: `curl localhost:8080/cuentas/002180000000000001`.

Terminado cuando: ves tu cuenta con saldo 1000 en la respuesta del paso 4.

## Parte 2 · Las propiedades de Compose, con tus manos (≈35 min)
1. `docker network inspect mexibanco`: confirma que `app` y `db` están en la misma red, y que
   ninguno tiene una IP fija que hayas escrito tú en ningún lado.
2. Abre una segunda cuenta (otra CLABE) y haz una transferencia entre las dos:
   ```
   curl -X POST localhost:8080/transferencias -H "Content-Type: application/json" \
     -d '{"claveOrigen":"...","claveDestino":"...","monto":200}'
   ```
   Consulta ambas cuentas otra vez y confirma que los saldos cuadran.
3. **Mata `app` a propósito**: `docker compose stop app`. Intenta pegarle con `curl`: no responde.
   Espera 15 segundos. ¿Volvió solo? (no). Eso es exactamente "lo que Compose no hace" del guion
   de hoy, comprobado por ti, no contado por el profesor.
4. Levántalo de nuevo: `docker compose start app`. Confirma que tu cuenta y tu transferencia
   siguen ahí (el volumen de `db` no se tocó).

Terminado cuando: hiciste una transferencia exitosa, mataste `app`, confirmaste que no revivió
sola, y la volviste a levantar sin perder datos.

## Parte 3 · Rompe la idempotencia, a propósito (≈20 min)
1. Manda un SPEI con una `Idempotency-Key` tuya (cualquier texto único, p. ej. tu CLABE + un número):
   ```
   curl -X POST localhost:8080/spei -H "Content-Type: application/json" -H "Idempotency-Key: prueba-001" \
     -d '{"claveOrigen":"...","bancoDestino":"BBVA","claveDestino":"012180000000000099","monto":50}'
   ```
2. Manda exactamente la misma petición otra vez, con la misma `Idempotency-Key`.
3. Consulta tu cuenta: ¿se descontó una vez o dos? Debe ser una sola vez, aunque mandaste la
   petición dos veces: el mismo patrón de la T01, ahora en un sistema completo.

Terminado cuando: puedes explicar, con tus palabras, por qué la segunda petición no cobró de nuevo.

## Reto (opcional)
Intenta `docker compose up --scale app=3` tal cual: falla, porque el puerto 8080 del host solo
se puede ocupar una vez. Cambia en `docker-compose.yml` la línea `"8080:8080"` por el rango
`"8080-8082:8080"` y vuelve a escalar: ahora hay 3 copias, en 8080, 8081 y 8082. Manda 10
peticiones a `GET /cuentas/{clabe}` en el 8080: ¿algo las reparte entre las 3 copias? (no: son
tres puertos distintos y Compose no trae balanceador). Anota tu observación: en la S06 vemos que
un Service de Kubernetes sí reparte, y esa es exactamente la diferencia.

## Entrega
En tu rama `entregas_apellido_nombre`, carpeta `entregas/apellido_nombre/s05/`: un `README.md`
con (a) la captura de `docker compose ps` mostrando ambos servicios `healthy`, (b) la respuesta
completa de tu segundo intento de SPEI (mostrando que no se duplicó el movimiento), y (c) tus
respuestas a las preguntas de cierre. Sin PR, solo push: el push es la entrega.

En tiempo hasta el **domingo 2026-09-27 a las 23:59** (se movió del 20: en la clase el laboratorio
se hizo como demostración). Tarde sin penalización de lunes a sábado, antes de la S07 (2026-10-03).
Lo que cuenta es la hora del último push a tu rama, no la fecha del commit.

## Preguntas de cierre
1. ¿Qué te dijo Compose que ya sabías, y qué te sorprendió?
2. Mataste `app` a propósito: ¿qué tendrías que agregar a `docker-compose.yml` para que sí se
   reiniciara sola (aunque siga sin resolver "otra máquina")? ¿Qué seguiría sin resolver aunque lo
   agregaras?
3. ¿Qué sacrificaste al no tener un balanceador entre las 3 copias del Reto, y qué pieza del
   curso resuelve justo eso?
