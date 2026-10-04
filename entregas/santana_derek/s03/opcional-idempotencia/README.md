# S03 opcional · Cobro simulado con Idempotency-Key

**Alumno:** Derek Santana. **Curso:** Sistemas Distribuidos, profesor Oscar Ruiz.
**Nivel:** completo. Es la actividad opcional de S03; el laboratorio anterior permanece
en `s02/`. Aquí no hay bancos, tarjetas ni dinero real.

## Ejecutar en la Mac

Con Docker Desktop abierto, entra en esta carpeta desde Terminal:

```bash
cd /Users/derek/Documents/sd-2027-1-derek/entregas/santana_derek/s03/opcional-idempotencia
```

Levanta el servicio (la primera vez descarga la imagen de Python):

```bash
docker compose up -d --build --wait
```

Ejecuta el cliente incluido. Envía **dos solicitudes HTTP simultáneas** con la misma
clave, luego un reintento y luego otro monto con esa clave:

```bash
python3 probar_http.py
```

Cada ejecución genera una clave nueva. Deben aparecer dos respuestas `201` con el mismo
`cobro_id`: una con `Idempotency-Replayed=false` y otra con `true`. El reintento reproduce
exactamente el cuerpo; el monto distinto recibe `409`. El orden de las primeras dos
respuestas puede cambiar. El script comprueba estos resultados y termina con `OK`;
un incumplimiento termina con error.

Para ejecutar el cliente dentro de Docker, sin Python local:

```bash
docker compose exec cobros python probar_http.py --url http://127.0.0.1:8080
```

Las pruebas automáticas usan un contenedor separado y bases temporales; no modifican
los cobros del servicio:

```bash
docker compose run --rm --no-deps cobros python tests.py
```

Para detenerlo conservando los datos:

```bash
docker compose down
```

El volumen `datos` conserva la base al reiniciar o recrear el contenedor dentro del mismo
proyecto Compose. **No uses `down -v` si quieres conservar el historial.** Una carpeta con
otro nombre o un `-p` distinto usa otro proyecto/volumen. El puerto publicado es
`127.0.0.1:8083`; si está ocupado, detén el servicio que lo usa o cambia el puerto y pasa
`--url http://127.0.0.1:PUERTO` al cliente.

Alternativa local, sin paquetes adicionales: en una ventana ejecuta `python3 app.py`;
en otra, desde esta carpeta, `python3 probar_http.py --url http://127.0.0.1:8080`.
Las pruebas locales se ejecutan con `python3 tests.py`. La base local se guarda en
`data/`, excluida de Git.

## Contrato del endpoint

`POST /cobros`, `Content-Type: application/json` y un solo header `Idempotency-Key`.
La clave tiene entre 8 y 128 caracteres: letras ASCII, números, `_` o `-`.
El JSON lleva exactamente estos campos:

```json
{"monto_centavos":12500,"moneda":"MXN","referencia":"pedido-001"}
```

12500 centavos representan 125.00 MXN. El monto es un entero entre 1 y 100000000
(no booleano ni decimal); sólo se admite MXN; la referencia es texto no vacío de hasta
100 caracteres. El cuerpo tiene un límite de 4096 bytes. Se rechazan JSON inválido,
campos repetidos, extras, claves faltantes y texto no codificable en UTF-8.
Se requiere `Content-Length`; no se admite envío por chunks.

| Situación | HTTP | Resultado |
| --- | --- | --- |
| Clave nueva y datos válidos | 201 | Cobro simulado; header de replay `false` |
| Misma clave y mismos datos | 201 | Código y cuerpo originales; header de replay `true` |
| Misma clave y otros datos | 409 | Conflicto; ningún cobro adicional |
| Clave/JSON/campos inválidos | 400 | Ninguna reserva ni cobro |
| Tipo de contenido incorrecto | 415 | Ninguna reserva ni cobro |
| Cuerpo vacío o mayor de 4096 bytes | 413 | Ninguna reserva ni cobro |
| Espera por el escritor superior a 5 segundos | 503 | `Retry-After: 1`; reintentar con la misma clave |
| Tiempo agotado recibiendo el cuerpo | 408 | Ningún cobro |

`GET /salud` comprueba que el servidor HTTP responde, sin inspeccionar el almacenamiento.
El orden de campos y espacios del JSON no afecta la comparación: se guarda una
representación canónica. Los valores sí cuentan, incluidos los espacios dentro de
`referencia`. No se almacenan tarjetas ni datos de pago.

## Por qué falla el Map

La versión ingenua hace esto:

```text
si clave NO está en Map:      ← check
    resultado = cobrar()     ← use
    Map[clave] = resultado
devolver Map[clave]
```

A verifica que la clave no existe. Antes de que A guarde algo, B verifica lo mismo.
Ambas cobran y después guardan: hay dos efectos aunque quede una sola entrada en el
Map. Esa separación entre comprobar y usar es una carrera TOCTOU. Que una lectura o
escritura aislada sea segura no vuelve atómica toda la secuencia. Además, un Map normal
se pierde al reiniciar y no se comparte entre procesos. La prueba 13 coloca una barrera
después del check y demuestra dos cobros en memoria.

Un mutex podría proteger la secuencia dentro de un proceso, pero no da persistencia ni
coordina por sí mismo varias instancias. Aquí la base decide la unicidad para todas las
conexiones al mismo archivo: `operaciones.clave TEXT NOT NULL UNIQUE`.
`cobros.operacion_id` también es `UNIQUE` y tiene una clave foránea.

## Transacción y respuestas simultáneas

El servidor usa hilos y una conexión SQLite por solicitud. No deduplica con un Map ni
con un mutex de Python. La secuencia es:

```text
validar y canonizar solicitud
BEGIN IMMEDIATE
INSERT clave, solicitud, fecha, estado=reservada  ← ANTES del efecto
si falla por clave duplicada:
    leer registro original y terminar transacción
    si los datos cambiaron: responder 409
    si son iguales: devolver código y cuerpo guardados
si es nueva:
    INSERT cobro simulado
    guardar estado=completada, código HTTP y cuerpo de respuesta
COMMIT
enviar respuesta HTTP
```

La reserva, el efecto local y el resultado se confirman juntos. El estado `reservada`
es interno a la transacción; no queda una reserva pendiente confirmada entre pasos.
SQLite permite un escritor a la vez: `BEGIN IMMEDIATE` hace que la segunda solicitud
espere a la primera. Después intenta su INSERT, recibe el error de `UNIQUE`, lee el
resultado y lo reproduce. El retraso de 0.2 segundos hace visible la contención;
no representa una llamada a un banco.

En el caso normal, **ambas solicitudes simultáneas reciben `201` y el mismo cuerpo**,
pero sólo la ganadora crea el cobro. `Idempotency-Replayed` distingue original y replay.
Se conserva el código original, incluso `201`; los headers variables de transporte
(por ejemplo `Date`) no se guardan. Si la base sigue ocupada después de 5 segundos,
se responde `503` sin cobrar; el cliente debe reintentar con la misma clave y datos.

Se guarda solicitud canónica, fecha, estado, código HTTP y cuerpo exacto con el UUID
del cobro. Guardar únicamente la clave evitaría otro INSERT, pero no permitiría
responder cuál fue el resultado original.

## Caídas y caducidad

| Punto de caída | Qué queda | Reintento |
| --- | --- | --- |
| Después del INSERT de reserva, antes del cobro | La transacción no confirmada se revierte | Reserva y cobra una vez |
| Después del INSERT del cobro, antes del COMMIT | Se revierten reserva y cobro | Completa una sola operación |
| Después del COMMIT, antes de responder | Quedan clave, cobro y resultado juntos | Reproduce sin volver a cobrar |

Las pruebas 6 a 8 terminan un **proceso real con `os._exit(77)`**, sin ejecutar el
`finally` de Python. Luego consultan SQLite y reintentan. Se usa WAL y
`synchronous=FULL`, con almacenamiento en un volumen de Docker. Las verificaciones
comprueban caída de proceso y persistencia entre recreaciones; no simulan cortes de
electricidad, corrupción del disco ni pérdida de la máquina virtual de Docker.

**Las claves no expiran en esta actividad.** El resultado se conserva mientras exista
el volumen; se guarda la fecha para una futura política. Esto consume espacio, pero
evita otro cobro ante un reintento tardío. Borrar una clave permitiría cobrar otra vez
una solicitud antigua. Un sistema real debe definir una ventana de retención según el
negocio y los reintentos, comunicarla y conservar un historial o identidad de operación
que impida duplicados tardíos. Aquí no se implementa caducidad automática.

## Límite frente a pagos externos

El efecto simulado es una fila dentro de **la misma base** y participa en el COMMIT.
Una API externa no participa en la transacción SQLite. Si el proveedor cobra y el
proceso cae antes de guardar el resultado, un rollback local no deshace ese cobro.
`UNIQUE` por sí solo no resuelve ese intervalo; confirmar primero una reserva tampoco
basta, pues podría quedar bloqueada sin saber si se cobró.

Haría falta una identidad estable enviada como clave idempotente al proveedor, estados
persistentes y recuperación que consulte/reconcilie el resultado ambiguo antes de
intentar otra vez. Una cola/outbox podría persistir el trabajo pendiente, pero por sí
sola tampoco evita efectos externos duplicados. Este ejercicio no implementa un
proveedor, conciliación ni garantiza exactamente una ejecución en sistemas externos.

Es didáctico: no tiene usuarios, autenticación, TLS ni cuotas. Las claves son globales;
un sistema multiusuario debería asociarlas a cliente y operación. SQLite serializa
incluso claves diferentes y limita el rendimiento. El volumen usa almacenamiento local:
esto no es una base distribuida ni admite instancias con archivos independientes o
WAL en un filesystem de red.

## Pruebas y evidencia

`tests.py` incluye 13 pruebas: dos y doce solicitudes HTTP concurrentes, replay al
reabrir la base, conflicto de datos, validación sin reservas, tres puntos de caída,
UNIQUE directo, concurrencia entre procesos, claves diferentes, base ocupada y la
carrera determinista del Map. Comprueba conteos en ambas tablas, además de las respuestas.
Cada prueba usa almacenamiento temporal y lo elimina al terminar.

Consulta [evidencias/README.md](evidencias/README.md) para los comandos ejecutados,
resultados y salidas reales. Los UUID y fechas de otra ejecución serán distintos.
La comprobación guiada del alumno en su copia local también terminó con `Healthy` y
`OK`; su salida está en
[comprobacion-manual-alumno.txt](evidencias/comprobacion-manual-alumno.txt), identificada
como proporcionada por Derek. Quedan pendientes el commit y push.

## Entrega en la rama del semestre

El desarrollo partió del commit `3566a8a`, `S02: corregir evidencias con pruebas reales
en Docker`, de `entregas_santana_derek`. Sólo se agregan archivos bajo
`entregas/santana_derek/s03/`; S02 y los materiales del profesor se conservan.

Tras incorporar S03 y ejecutar la comprobación guiada, abre **SD** de
`/Users/derek/Documents/sd-2027-1-derek` en GitHub Desktop. Verifica que **Current Branch**
sea `entregas_santana_derek` y que los cambios sean únicamente de
`entregas/santana_derek/s03/`. Revisa los archivos, guarda el commit con el mensaje
`S03: actividad opcional de idempotencia con pruebas reales` y usa **Push origin**.
El push es la entrega; las guías indican **un solo PR al final del curso**.
Esta preparación no crea commit, push ni PR.

## Fuentes

- [Actividad opcional oficial](https://github.com/OscarRuiz21/sd-2027-1/blob/main/entregas/OPCIONALES.md), consultada el 3 de octubre de 2026.
- [Guía de entregas](https://github.com/OscarRuiz21/sd-2027-1/blob/main/entregas/README.md) y [rutina Git](https://github.com/OscarRuiz21/sd-2027-1/blob/main/GIT-CHEATSHEET.md), consultadas en main sin mezclar ramas.
- [Transacciones de SQLite](https://www.sqlite.org/lang_transaction.html): un escritor simultáneo y `BEGIN IMMEDIATE`.
- [WAL de SQLite](https://www.sqlite.org/wal.html): sincronización y restricciones del almacenamiento compartido.
- [Atomicidad de SQLite](https://www.sqlite.org/atomiccommit.html): alcance y supuestos sobre almacenamiento.
