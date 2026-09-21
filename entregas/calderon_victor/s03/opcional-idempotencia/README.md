# Opcional S03 · Implementación del patrón de Idempotency Key

**Autor:** Victor Emiliano Calderón Gutiérrez  

---

## 1. Guía rápida de ejecución

### 1.1. Levantar el servicio
```bash
cd entregas/calderon_victor/s03/opcional-idempotencia/
docker compose up --build -d
```
El servicio iniciará en `http://localhost:8080`.

### 1.2. Ejecutar la suite de pruebas
```bash
chmod +x test.sh
./test.sh
```

### 1.3. Detener el servicio
```bash
docker compose down
```

---

## 2. Anatomía del problema y solución técnica

### 2.1. Vulnerabilidad TOCTOU en memoria (Endpoint ingenuo)
En la implementación inicial con un `Map` en memoria (`ConcurrentHashMap`), la verificación de existencia (`check`) y el registro del resultado (`use`) son operaciones desacopladas. Entre ambas existe una ventana temporal de vulnerabilidad (*Time-of-Check to Time-of-Use* / TOCTOU):

| Paso | Petición A (Hilo 1) | Petición B (Hilo 2) | Estado del Map |
| :---: | :--- | :--- | :--- |
| **t0** | `containsKey(K)` → `false` | — | Vacío |
| **t1** | Inicia procesamiento (latencia) | `containsKey(K)` → `false` | Vacío |
| **t2** | Finaliza cobro y guarda `K` | Inicia procesamiento (latencia) | Registra `K` |
| **t3** | Responde `200 OK` (Cobro 1) | Finaliza cobro y sobrescribe `K` | Registra `K` (**Doble cobro**) |

### 2.2. Atomicidad mediante restricción UNIQUE en Base de Datos (Endpoint robusto)
Para garantizar atomicidad, se invierte el orden: **primero se inserta la llave en la base de datos y luego se procesa el cobro**. La restricción `PRIMARY KEY` a nivel de motor relacional evalúa la existencia y la reserva de la llave en una sola instrucción indivisible.

#### Definición del esquema (`schema.sql`):
```sql
CREATE TABLE idempotency_keys (
    key_id        VARCHAR(64)  PRIMARY KEY,
    status        VARCHAR(20)  NOT NULL,
    response_body TEXT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

#### Mecánica de control en `Application.java`:
```java
try {
    // 1. Reserva atómica en estado PENDING
    jdbc.update("INSERT INTO idempotency_keys (key_id, status) VALUES (?, 'PENDING')", idempotencyKey);

    // 2. Simulación de procesamiento de cobro (llamada bancaria externa)
    Thread.sleep(200);

    String transaccionId = "txn-" + Instant.now().toEpochMilli();
    String responseBody = String.format(
            "{\"status\":\"cobrado\",\"transaccion\":\"%s\",\"monto\":500,\"llave\":\"%s\"}",
            transaccionId, idempotencyKey
    );

    // 3. Transición a COMPLETED con el resultado guardado
    jdbc.update("UPDATE idempotency_keys SET status='COMPLETED', response_body=? WHERE key_id=?",
            responseBody, idempotencyKey);

    return ResponseEntity.ok(Map.of(
            "status", "cobrado",
            "transaccion", transaccionId,
            "monto", 500,
            "llave", idempotencyKey
    ));

} catch (DuplicateKeyException ex) {
    // El motor rechazó el INSERT por colisión en PRIMARY KEY
    Map<String, Object> row = jdbc.queryForMap(
            "SELECT status, response_body FROM idempotency_keys WHERE key_id=?", idempotencyKey);

    String status = (String) (row.get("STATUS") != null ? row.get("STATUS") : row.get("status"));
    String responseBody = (String) (row.get("RESPONSE_BODY") != null ? row.get("RESPONSE_BODY") : row.get("response_body"));

    if ("PENDING".equals(status)) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Operación en proceso, reintente más tarde"));
    }

    return ResponseEntity.ok(Map.of(
            "status", "ya_procesado",
            "mensaje", "Resultado original del primer cobro exitoso",
            "resultado_original", responseBody != null ? responseBody : "",
            "llave", idempotencyKey
    ));
}
```

#### Tabla de resolución concurrente en BD:
| Paso | Petición A (Hilo 1) | Petición B (Hilo 2) | Estado en BD |
| :---: | :--- | :--- | :--- |
| **t0** | Ejecuta `INSERT ... PENDING` (éxito) | — | Fila insertada (`PENDING`) |
| **t1** | Procesando llamada bancaria (`sleep`) | Ejecuta `INSERT ... PENDING` | Lanza `DuplicateKeyException` |
| **t2** | Concluye cobro y ejecuta `UPDATE ... COMPLETED` | Captura excepción, lee `status='PENDING'` y responde `409 Conflict` | Fila pasa a `COMPLETED` |
| **t3** | Responde `200 OK` | — | Persistido (`COMPLETED`) |

---

## 3. Desglose detallado de pruebas (`test.sh`)

El script `test.sh` dispara peticiones concurrentes en segundo plano (`&`) y valida las respuestas obtenidas para cada escenario:

### 3.1. Prueba 1 — Falla del Map (`/api/cobro-ingenuo`)
Demuestra la vulnerabilidad TOCTOU lanzando dos peticiones concurrentes con la misma llave. Ambas pasan la comprobación de existencia antes de que cualquiera guarde el resultado, produciendo un doble cobro:

```bash
curl -X POST http://localhost:8080/api/cobro-ingenuo \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: llave-ingenua-001" \
  -d '{"monto": 500}' &

curl -X POST http://localhost:8080/api/cobro-ingenuo \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: llave-ingenua-001" \
  -d '{"monto": 500}' &
```
- **Resultado esperado:** Ambas peticiones retornan `200 OK` con transacciones distintas (doble cobro confirmado).

### 3.2. Prueba 2 — Éxito del UNIQUE (`/api/cobro`)
Demuestra el control atómico de concurrencia en la base de datos lanzando dos peticiones en paralelo con la misma llave:

```bash
curl -X POST http://localhost:8080/api/cobro \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: llave-robusta-001" \
  -d '{"monto": 500}' &

curl -X POST http://localhost:8080/api/cobro \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: llave-robusta-001" \
  -d '{"monto": 500}' &
```
- **Resultado esperado:** La primera en ejecutar el `INSERT` reserva la llave (`PENDING`) y procesa el cobro (`200 OK`); la segunda colisiona con la restricción `PRIMARY KEY` durante el estado `PENDING` y es rechazada con `409 Conflict` (`Operación en proceso, reintente más tarde`).

### 3.3. Prueba 3 — Reintento seguro (`/api/cobro`)
Demuestra la respuesta idempotente reintentando la petición una vez que la transacción previa alcanzó el estado `COMPLETED`:

```bash
curl -X POST http://localhost:8080/api/cobro \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: llave-robusta-001" \
  -d '{"monto": 500}'
```
- **Resultado esperado:** Retorna `200 OK` entregando exactamente el cuerpo almacenado en `response_body` sin reejecutar el cobro.

---

## 4. Preguntas de diseño

### 4.1. ¿Qué guardas junto a la llave para poder devolver el resultado original en el reintento?
Se almacena el estado (`status`), el cuerpo completo de la respuesta formateada (`response_body`), la marca temporal de creación (`created_at`) y el identificador de la llave (`key_id`). En arquitecturas distribuidas de producción se suele incluir adicionalmente un hash del payload (`request_hash`) para rechazar reintentos con parámetros modificados (`422 Unprocessable Entity`).

```json
{
  "status": "cobrado",
  "transaccion": "txn-1789976892163",
  "monto": 500,
  "llave": "mi-llave-uuid"
}
```

### 4.2. ¿Cuándo expira una llave?
En esta implementación demostrativa las llaves se conservan en memoria durante la vida del proceso. En producción se establece una ventana de retención (TTL) de 24 a 48 horas (estándar de la industria). La columna `created_at` permite a un proceso programado ejecutar la depuración periódica:
```sql
DELETE FROM idempotency_keys WHERE created_at < NOW() - INTERVAL '24' HOUR;
```

### 4.3. ¿Qué pasa si el proceso muere DESPUÉS del INSERT pero ANTES de cobrar?
Si el proceso se interrumpe tras registrar la llave en `PENDING` pero antes de completar el cobro y actualizar a `COMPLETED`, la fila queda como registro huérfano. Reintentos subsiguientes recibirán `409 Conflict`.

Para resolver este escenario en producción:
1. **Timeout sobre PENDING:** Si un registro en `PENDING` excede un tiempo límite (ej. 2 minutos), se asume fallo en el nodo emisor.
2. **Reconciliación y compensación:** Un worker en segundo plano consulta el estado real en la pasarela de pagos. Si no existe cobro registrado externamente, la llave se marca como `FAILED` o se elimina para permitir un nuevo intento legítimo del cliente.

---

## 5. Evidencia de ejecución

Salida íntegra obtenida al ejecutar `./test.sh` contra el contenedor en ejecución:

```text
Verificando conexión con http://localhost:8080...
✓ Servidor listo

[Prueba 1] POST /api/cobro-ingenuo (concurrente)
  Respuestas: HTTP 200 / HTTP 200
  Veredicto:  PASS (TOCTOU detectado: doble cobro ejecutado)

[Prueba 2] POST /api/cobro (concurrente)
  Respuestas: HTTP 200 / HTTP 409
  Veredicto:  PASS (Atomicidad en BD: 1 procesado 200 OK, 1 conflicto 409)

[Prueba 3] POST /api/cobro (reintento)
  Respuesta:  HTTP 200
  Veredicto:  PASS (Reintento seguro: respuesta previa retornada sin reejecutar)
```

---

## 6. Respuestas HTTP del endpoint robusto

| Escenario | Código HTTP | Condición |
| :--- | :---: | :--- |
| Cobro exitoso | `200 OK` | `INSERT` exitoso, estado actualizado a `COMPLETED` |
| Header ausente | `400 Bad Request` | Encabezado `Idempotency-Key` faltante o vacío |
| En proceso | `409 Conflict` | La llave existe en estado `PENDING` |
| Reintento completado | `200 OK` | La llave existe en estado `COMPLETED`; retorna payload original |
