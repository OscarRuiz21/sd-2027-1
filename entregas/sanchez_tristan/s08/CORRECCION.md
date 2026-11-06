# Corrección · Lab S08: Saga Orquestada y Circuit Breaker

**Alumno:** Sánchez Mayén Tristán Qesen  
**Cuenta:** 320187072  
**Corte:** 4-nov-2026  

---

## Observación de la revisión
> **Qué hay que corregir:**  
> S08: no explicas por qué la compensación debe ser idempotente.  
>  
> **Cómo se corrige:**  
> Agrega un párrafo: si la devolución se aplica pero la respuesta se pierde, el reintento devolvería el dinero dos veces; di cómo lo evitarías (por ejemplo, con una clave fija por transferencia).

---

## 1. ¿Por qué la compensación debe ser estrictamente idempotente?

En una arquitectura distribuida que implementa una **Saga orquestada** (como la del servicio de `transferencia` en Mexi Banco), el proceso de transferencia consta de múltiples transacciones locales independientes:
1. Se carga a la cuenta de origen (`cuentas.cargar(origen, monto)`).
2. Se intenta abonar a la cuenta de destino (`cuentas.abonar(destino, monto)`).

Si el abono a la cuenta de destino falla (por ejemplo, porque el servicio `cuenta` arrojó un error 503 o sufrió una caída), el orquestador (`TransferenciaService`) entra en su bloque `catch` y ejecuta la **transacción compensatoria**: devolver el dinero a la cuenta de origen mediante una llamada HTTP de abono (`cuentas.abonar(origen, monto)`).

Sin embargo, en redes distribuidas reales las comunicaciones son inherentemente falibles:
- Puede ocurrir que la llamada de compensación llegue exitosamente al servicio `cuenta`, el servicio ejecute el abono en su base de datos PostgreSQL e incremente el saldo de origen, pero **la respuesta HTTP de éxito (200 OK) se pierda en la red** por una partición temporal o sufra un *timeout* antes de llegar al orquestador.
- Ante la falta de respuesta o ante un error de timeout, el orquestador no tiene forma de saber si la petición falló antes de procesarse o después de aplicarse. En consecuencia, el mecanismo de reintentos (o el job asíncrono que procesa transferencias en estado `COMPENSACION_PENDIENTE`) **volverá a enviar la petición de reembolso**.
- **Si la compensación no es idempotente, el reintento devolvería el dinero dos veces** (o tantas veces como reintentos se efectúen): el servicio `cuenta` recibiría la segunda petición, la interpretaría como una transacción financiera completamente nueva y volvería a sumar el monto al saldo del cliente. Esto provocaría una inconsistencia contable crítica en el banco, generando dinero de la nada.

Por esta razón fundamental de diseño distribuido: **reintentar sin idempotencia cobra o devuelve dos veces**.

---

## 2. ¿Cómo evitar la doble devolución? (Solución con clave fija de idempotencia)

Para garantizar que la compensación sea 100% segura e idempotente ante cualquier número de reintentos en la red, cada operación compensatoria debe vincularse a una **clave fija y determinista asociada unívocamente a la transferencia original**.

### Diseño del mecanismo:

1. **Generación de la clave:**  
   Al disparar la compensación, el orquestador genera una clave basada en el identificador único de la transferencia (por ejemplo, `compensacion-tx-4` o un UUID persistido en la entidad `Transferencia`), y la envía al servicio `cuenta` mediante una cabecera HTTP estándar:
   ```http
   Idempotency-Key: compensacion-tx-4
   ```
   o bien en el cuerpo de la petición.

2. **Validación atómica en `cuenta`:**  
   En la base de datos del servicio `cuenta`, se mantiene una tabla de operaciones procesadas con una restricción de unicidad (`UNIQUE`) sobre la columna de la clave:
   ```sql
   CREATE TABLE operacion_idempotente (
       clave_idempotencia VARCHAR(64) PRIMARY KEY,
       clave_cuenta VARCHAR(18) NOT NULL,
       monto NUMERIC(15,2) NOT NULL,
       saldo_resultante NUMERIC(15,2) NOT NULL,
       fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );
   ```

3. **Flujo de ejecución:**
   - **Primer intento:** El servicio `cuenta` verifica si la clave `compensacion-tx-4` ya existe. Al no encontrarla, ejecuta el abono al saldo, inserta el movimiento en el ledger y registra la clave de idempotencia junto con el saldo resultante dentro de la misma transacción local ACID (`@Transactional`). Retorna `HTTP 200 OK`.
   - **Reintento ante respuesta perdida:** Si la respuesta del primer intento se pierde y el orquestador reintenta enviando nuevamente `Idempotency-Key: compensacion-tx-4`, el servicio `cuenta` detecta que la clave ya fue registrada previamente en la base de datos.
   - **Respuesta idempotente:** En lugar de volver a sumar dinero al saldo de la cuenta, el servicio ignora la mutación y simplemente devuelve la respuesta previamente registrada con el saldo resultante original (`HTTP 200 OK`).

De esta manera, sin importar si la red se corta, si la compensación se reintenta 1 vez o 20 veces, **el dinero únicamente se devuelve una sola vez**.
