# Opcional S03 · Implementa bien el patrón de idempotency key

**Pablo Vaquero · Nivel completo · Python + SQLite**

Este mini servicio recibe `POST /charges` y evita duplicar un cobro cuando el cliente
reintenta con la misma `Idempotency-Key`. El cobro es una fila en la tabla `charges`:
es una simulación local, sin conexión a un banco ni movimiento de dinero.

La llave se inserta **antes** del cobro y tiene una restricción `UNIQUE`. La llave,
el cobro y la respuesta se guardan en **una sola transacción**.

## Ejecutar

Requiere Python 3.11 o posterior. No hay paquetes externos que instalar.
Las pruebas automatizadas están preparadas para macOS/Linux (en Windows, usar WSL).
Desde esta carpeta:

```bash
python3 app.py --delay 0.5
```

El servicio escucha en `http://127.0.0.1:8000`. `--delay 0.5` agrega medio segundo
después de insertar la llave para facilitar que dos peticiones se traslapen. No es
parte del mecanismo de exclusión; sin esta opción se conserva la misma garantía.
Para detenerlo, presionar `Ctrl+C`.

Se crea `charges.sqlite3` junto a `app.py`. El archivo conserva los resultados al
reiniciar y está excluido de Git. Es posible elegir otro archivo con `--db ruta.sqlite3`.

## Probar dos peticiones simultáneas

Dejar el servicio anterior abierto y ejecutar en otra terminal, desde esta carpeta:

```bash
python3 demo_race.py
```

El programa genera una llave nueva y usa una barrera para lanzar dos clientes HTTP
al mismo tiempo. Imprime el resultado de cada uno y comprueba que los cuerpos sean
idénticos. El orden de llegada puede variar:

| Petición | HTTP | `Idempotency-Replayed` | Efecto |
| --- | --- | --- | --- |
| La que obtiene la llave | `201 Created` | `false` | Crea una fila en `charges` |
| La que encuentra el duplicado | `201 Created` | `true` | Devuelve el cuerpo ya guardado |

Ambas reciben el mismo `id`, importe, moneda y estado. El segundo `201` reproduce
el código original; no indica que se haya creado otro cobro. Los encabezados de
transporte, como `Date`, pueden cambiar; el código y los bytes del cuerpo se conservan.

También se puede ver la respuesta manualmente:

```bash
curl -i http://127.0.0.1:8000/charges \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pedido-s03-001' \
  -d '{"amount":10000,"currency":"MXN"}'
```

Ejecutar exactamente el mismo comando otra vez devuelve el resultado original.
`amount` usa **centavos enteros**: `10000` representa 100.00 MXN. Si se conserva la
llave pero se cambia `amount` a `20000`, la respuesta es `409 Conflict`.
Cada operación nueva requiere una llave nueva; cada reintento conserva la anterior.

Para contar las filas por llave en la base local, desde esta carpeta:

```bash
python3 -c 'import sqlite3; c=sqlite3.connect("charges.sqlite3"); print(c.execute("SELECT idempotency_key, COUNT(*) FROM charges GROUP BY idempotency_key").fetchall()); c.close()'
```

Cada llave debe tener una fila. Cada ejecución de `demo_race.py` utiliza otra llave,
así que el total de cobros crece en uno por demostración.

## Por qué falla el Map

La versión ingenua hace dos cosas separadas: consultar y, después de cobrar, guardar.
En este pseudocódigo, `cobrar` puede tardar o ceder la ejecución:

```text
resultados = Map()

cobrar_con_llave(llave, datos):
    si resultados.contiene(llave):
        devolver resultados[llave]

    resultado = cobrar(datos)
    resultados[llave] = resultado
    devolver resultado
```

Dos peticiones pueden intercalarse así:

| Paso | Petición A | Petición B |
| --- | --- | --- |
| 1 | Consulta K: no existe | |
| 2 | | Consulta K: no existe |
| 3 | Cobra | |
| 4 | | Cobra |
| 5 | Guarda el resultado en K | |
| 6 | | Sobrescribe K con su resultado |

Queda una entrada en el Map, pero ya hubo dos cobros. Esa ventana entre consultar
y actuar es la carrera **TOCTOU**. Además, el Map se pierde al reiniciar y cada
proceso tendría su propia copia. Un mutex local podría ordenar los hilos de un
proceso, pero no coordinaría otros procesos ni conservaría el resultado tras una caída.

## Por qué funciona esta versión

La base de datos hace cumplir `key TEXT NOT NULL UNIQUE`. La aplicación intenta
insertar directamente; no decide si puede cobrar mediante una consulta previa.
`NOT NULL` también importa: SQLite permite varios valores nulos bajo `UNIQUE`.
Véase la [documentación de restricciones UNIQUE de SQLite](https://www.sqlite.org/lang_createtable.html#unique_constraints).

```text
validar llave y datos
hash = SHA256(JSON canónico de los datos)
BEGIN

intentar INSERT llave y hash
si falla por llave duplicada:
    ROLLBACK de este intento
    leer el registro confirmado de esa llave
    si el hash no coincide: devolver 409
    devolver el código y el cuerpo originales

INSERT del cobro simulado
UPDATE de la llave con el código 201 y el cuerpo de la respuesta
COMMIT
devolver 201 y el cuerpo guardado
```

SQLite admite un escritor a la vez. Mientras A mantiene la transacción, B espera
para escribir. Si A confirma, el `INSERT` de B choca con `UNIQUE`; si A revierte,
B puede insertar y completar la operación. El servicio usa conexiones separadas,
también entre procesos, sobre **el mismo archivo de base de datos**. Véase la
[documentación de transacciones de SQLite](https://www.sqlite.org/lang_transaction.html).

La espera tiene un límite de cinco segundos (`--lock-timeout`). Si la base continúa
ocupada, se devuelve `503 Service Unavailable` con `Retry-After: 1`. El cliente debe
reintentar con la misma llave y los mismos datos. Un `503` por bloqueo no asegura
que otra petición haya fallado: puede seguir ejecutándose.

## Datos conservados y decisiones

| Dato | Para qué sirve |
| --- | --- |
| `key`, única y no nula | Identificar la operación lógica |
| `request_hash` | Rechazar que una misma llave se use para otro importe o moneda |
| `response_status` | Reproducir el código HTTP original |
| `response_body` | Reproducir exactamente los bytes JSON, incluido el ID original |
| `created_at` | Registrar cuándo se creó la entrada |

El hash se calcula con las claves JSON ordenadas y sin espacios accesorios. Cambiar
el orden de los campos o el formato del JSON no cambia la operación. Solo se admiten
`amount` y `currency`, para evitar ignorar datos que pudieran cambiar el significado.
Las entradas inválidas se rechazan antes de reservar una llave. Los errores internos
previos al `COMMIT` revierten la transacción; solo se conservan los cobros exitosos.

**Expiración:** en este ejercicio las llaves no expiran. La garantía dura mientras se
conserve la base; borrar sus registros la elimina. Una política con vencimiento
tendría que definir cuánto tiempo pueden llegar reintentos y qué hacer con solicitudes
tardías. Eliminar una llave y aceptar después el mismo pedido como nuevo permitiría
duplicar el efecto. No agregué una limpieza automática que ocultara ese problema.

## Qué pasa si el proceso muere

| Momento de la caída | Estado al recuperarse | Qué hace el reintento |
| --- | --- | --- |
| Después del `INSERT` de la llave, antes del cobro | La transacción sin confirmar se revierte | Puede insertar la llave y cobrar |
| Después del cobro local, antes del `COMMIT` | Se revierten el cobro y la llave juntos | Puede realizar la operación |
| Después del `COMMIT`, antes de entregar la respuesta | Llave, cobro y respuesta están guardados | Recupera la respuesta sin volver a cobrar |

La fila provisional de la llave no se confirma por separado: por eso no queda
una llave pendiente que bloquee para siempre los reintentos. Las pruebas terminan
un proceso con `os._exit`, sin ejecutar el bloque `finally`, para verificar la
recuperación de la base y no solo el manejo normal de excepciones.

## Pruebas

```bash
python3 -m unittest -v
```

Las pruebas crean bases temporales e inician servidores en puertos libres. Verifican:

- Dos peticiones simultáneas: mismo código y cuerpo, una creación y una repetición.
- Dos procesos de servidor distintos que comparten la misma base: un solo cobro.
- Llave repetida con datos diferentes, tanto en secuencia como en concurrencia: `409`.
- Distinto orden y espacios del JSON: mismo resultado original.
- Llaves diferentes: operaciones diferentes.
- Reinicio del servidor: persistencia de la respuesta.
- Datos inválidos: no se reserva la llave.
- Bloqueo de la base: `503`, seguido de un reintento exitoso.
- Caída tras reservar la llave y tras insertar el cobro: reversión antes del `COMMIT`.
- Caída después del `COMMIT`: recuperación de la respuesta guardada.
- Ejecución del programa `demo_race.py` contra un servidor real.

Además de comparar respuestas, las pruebas consultan las tablas para comprobar
cuántos cobros y llaves quedaron guardados.

## Alcance

La garantía cubre el **efecto local dentro de la misma transacción**. `UNIQUE` por sí
solo no vuelve atómica una llamada a un banco externo: el banco podría cobrar y
el servicio caerse antes de registrar el resultado. Para ese caso haría falta una
coordinación adicional, por ejemplo un proveedor que también admita una llave estable
y una recuperación que consulte o reintente esa misma operación. No bastaría con borrar
la llave pendiente y volver a cobrar.

Esta versión es didáctica y usa el servidor HTTP de la biblioteca estándar, enlazado
a localhost. No incluye autenticación. Tiene un solo endpoint con llaves globales;
con varios clientes autenticados o tipos de operación, el alcance debería incluir
cliente y operación, por ejemplo `UNIQUE(cliente_id, operacion, llave)`.
Dos réplicas con bases independientes no compartirían la protección. Para escalar
a varias máquinas usaría una base compartida apropiada, conservando la misma decisión
atómica mediante `UNIQUE`.

## Archivos

- `app.py`: endpoint, validación y transacción idempotente.
- `schema.sql`: tablas y restricciones.
- `demo_race.py`: dos clientes HTTP simultáneos.
- `test_app.py`: pruebas automatizadas.
- `EVIDENCIA.md`: resultado de la verificación de esta entrega.

## Entrega en el repositorio

La carpeta corresponde a `entregas/vaquero_pablo/s03/opcional-idempotencia/` en la
rama `entregas_vaquero_pablo`. Desde la raíz del repositorio, después de revisar:

```bash
git status
git add entregas/vaquero_pablo/s03/opcional-idempotencia
git commit -m "S03: implementar cobro idempotente con SQLite y pruebas de concurrencia"
git push origin entregas_vaquero_pablo
```

De acuerdo con la guía del curso, el push constituye la entrega y no se abre un
pull request para esta actividad.
