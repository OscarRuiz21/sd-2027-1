# Opcional S03 · Cobros con Idempotency Key

**Alumna:** Amy Veraza

Este mini servicio evita que un cobro se ejecute dos veces cuando el cliente repite una petición
porque perdió la respuesta. El cliente manda una clave en el header `Idempotency-Key`; el servidor
guarda esa clave en SQLite antes de crear el cobro.

## Ejecutar

```bash
docker compose up --build -d
python probar_carrera.py
```

Para limpiar la base y repetir la prueba:

```bash
docker compose down -v
```

El endpoint es `POST /cobros`:

```bash
curl -i -X POST http://localhost:8080/cobros \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: amy-cobro-001" \
  -d '{"monto":250,"concepto":"Inscripción"}'
```

La primera petición responde `201 Created`. Si se repite la misma petición con la misma clave,
responde `200 OK`, devuelve el resultado guardado con `repetida: true` y no crea otro cobro. Si se
intenta reutilizar la clave con un monto o concepto diferentes, responde `409 Conflict`.

## Prueba de la carrera

`probar_carrera.py` usa dos hilos y una barrera para enviar al mismo tiempo dos peticiones idénticas.
El servicio introduce una pausa de 400 ms después de reservar la clave para ampliar deliberadamente
la ventana de concurrencia. El resultado esperado es:

```text
Petición 1: HTTP 201 {... "id": 1, "repetida": false}
Petición 2: HTTP 200 {... "id": 1, "repetida": true}
Cobros guardados: 1
```

El orden de las peticiones puede invertirse, pero las dos respuestas deben contener el mismo `id` y
`GET /cobros` debe mostrar una sola fila.

## Por qué un `Map` puede fallar

Una implementación ingenua separa la revisión y el registro:

```text
si clave no está en mapa:
    realizar_cobro()
    mapa[clave] = respuesta
devolver mapa[clave]
```

Dos hilos pueden revisar el mapa antes de que cualquiera guarde la clave. Ambos ven que no existe y
los dos realizan el cobro. Esta carrera se conoce como TOCTOU: el estado puede cambiar entre el
momento de comprobarlo y el momento de usarlo. Además, un mapa en memoria se pierde al reiniciar el
proceso y no se comparte entre varias réplicas del servicio.

## Por qué `UNIQUE` evita el cobro doble

La tabla `idempotencia` usa `clave TEXT PRIMARY KEY`, que en SQLite impone unicidad. La reserva de la
clave y la creación del cobro ocurren en una transacción `BEGIN IMMEDIATE`. SQLite permite un solo
escritor; la segunda petición espera y, cuando entra, encuentra la respuesta que dejó la primera.

```text
iniciar transacción
buscar Idempotency-Key
si existe y el hash coincide:
    devolver la respuesta guardada
si existe y el hash no coincide:
    responder 409
insertar la clave como PROCESANDO       <- restricción UNIQUE
crear el cobro
guardar la respuesta como COMPLETADO
confirmar transacción
```

También guardo un hash del cuerpo. Una misma clave representa una sola operación y no debe ocultar
por accidente una petición diferente.

## Fallos, resultado y expiración

Junto a la clave se guardan el hash de la solicitud, el estado y la respuesta JSON. Esto permite que
un reintento reciba exactamente el resultado original.

En este ejemplo, la reserva y el cobro viven en la misma base y en la misma transacción. Si el proceso
muere después del `INSERT` pero antes de crear el cobro, SQLite revierte ambos cambios; el cliente
puede reintentar. En un sistema que llama a un banco externo no existe una transacción común. Allí se
necesitaría una operación idempotente en el proveedor o un patrón como outbox, además de un proceso
que recupere registros atascados en `PROCESANDO`.

Las claves no deberían borrarse mientras un cliente todavía pueda reintentar. En producción se fija
una política de retención según el negocio y se eliminan únicamente claves completadas después de ese
plazo; las claves en proceso requieren revisión o recuperación, no una eliminación automática.
