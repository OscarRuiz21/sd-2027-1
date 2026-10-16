# Lab S08: saga orquestada y circuit breaker

**Pablo Vaquero · Cuenta 318259624 · Grupo 2**

Pruebas ejecutadas el 15 de octubre de 2026, hora de Ciudad de México.

La transferencia guarda su avance, devuelve el cargo ante un rechazo confirmado del abono y deja un estado pendiente si la devolución no puede realizarse. Todas las llamadas de transferencia a cuenta pasan por un circuit breaker manual. La [bitácora](bitacora-s08.pdf) contiene las siete partes del enunciado, diagrama, capturas y explicaciones.

## Archivos

- `bitacora-s08.pdf`: informe de ocho páginas.
- `codigo/transferencia/`: servicio completo, pruebas y Maven Wrapper, sin `target/`.
- `codigo/docker-compose.yml`: entorno de S08 y variables de configuración.
- `evidencia/`: respuestas HTTP, consultas SQL, logs, capturas y script de los experimentos.

Base oficial de Mexi Banco: rama `03-gateway-discovery-balanceo`, commit `87cfbe97cdc9efc7c8a9a34f34a9c136ab9918f1`.
Enunciado revisado en `main` del curso: `2f198c45d883e5e33e41620f71bc9e4673876e30`.

## Cómo levantarlo

Clona la base oficial en otra carpeta. Copia encima la carpeta `transferencia/` y `docker-compose.yml` de `codigo/`. Los demás servicios y el script de inicialización de bases permanecen como están en la base oficial.

```bash
git clone --branch 03-gateway-discovery-balanceo \
  https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s08
cd mexi-banco-s08
git checkout 87cfbe97cdc9efc7c8a9a34f34a9c136ab9918f1
# Copia aquí codigo/transferencia/ y codigo/docker-compose.yml de esta entrega.
docker compose up --build -d --scale cuenta=1 --wait
```

El proyecto y su red se llaman `mexi-banco-s08` y `mexibanco-s08`. Se usa un volumen propio. El gateway está en `http://localhost:8080` y Eureka en `http://localhost:8761`. Espera a que los servicios estén registrados además de saludables. Para detener el entorno conservando sus datos: `docker compose down`.

## API y estados

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"claveOrigen":"002180000000008201","claveDestino":"002180000000008202","monto":200}' \
  http://localhost:8080/api/transferencias

curl http://localhost:8080/api/transferencias/1
curl http://localhost:8080/api/transferencias/circuito
```

Las cuentas deben existir antes de transferir. Se crean con `POST /api/cuentas`, enviando `clabe`, `titular` y `saldoInicial`.

| HTTP | Estado | Significado |
|---|---|---|
| 201 | `COMPLETADA` | Cargo, abono y movimientos confirmados. Un aviso fallido se explica en `detalle`. |
| 409 | `COMPENSADA` | El abono fue rechazado y el cargo devuelto, con movimientos registrados. |
| 422 | `RECHAZADA` | Rechazo de negocio; no se confirmó un cargo. |
| 503 | `NO_DISPONIBLE` | No se pudo continuar y no se confirmó un cargo. |
| 202 | `COMPENSACION_PENDIENTE` | El abono no se aplicó y la devolución sigue pendiente. |
| 202 | `REVISION_PENDIENTE` | No se conoce con certeza el resultado de un efecto remoto. |
| 202 | `REGISTRO_PENDIENTE` | El dinero ya se transfirió o se devolvió; falta conciliar el ledger. |

El cuerpo incluye `id`, `estado`, `detalle`, `historial` y fechas. Los estados previos a los saltos HTTP permiten identificar una caída entre el envío y la confirmación. Un 202 **no significa recuperación automática**: estas operaciones requieren conciliación.

## Configuración de las pruebas

| Variable | Predeterminado | Uso |
|---|---|---|
| `FALLAR_ABONO_CLABE` | vacía | Rechaza el abono a esa CLABE antes de enviarlo a cuenta. |
| `PAUSA_ENTRE_CARGO_Y_ABONO_MS` | 0 | Abre una ventana didáctica después del cargo. |
| `CUENTA_CB_UMBRAL` | 3 | Fallas de infraestructura consecutivas para abrir. |
| `CUENTA_CB_ESPERA_MS` | 15000 | Espera antes de permitir una prueba semiabierta. |
| `HTTP_CONEXION_MS` | 2000 | Límite de conexión HTTP. |
| `HTTP_LECTURA_MS` | 2000 | Límite de lectura HTTP. |
| `GATEWAY_RESPONSE_TIMEOUT` | 45s | Permite observar la respuesta de la saga con la pausa de 20 s. |

```bash
FALLAR_ABONO_CLABE=002180000000008202 docker compose up -d transferencia
PAUSA_ENTRE_CARGO_Y_ABONO_MS=20000 docker compose up -d transferencia
# Al terminar, desactiva las fallas y la pausa:
FALLAR_ABONO_CLABE= PAUSA_ENTRE_CARGO_Y_ABONO_MS=0 docker compose up -d transferencia
```

El gateway original agotó su límite de 15 s en la Parte 0 y devolvió 504. La transferencia no se canceló: el cargo quedó aplicado. Para la prueba equivalente con la saga, Compose amplía ese límite a 45 s sin modificar el código de gateway.

## Evidencia real

| Archivo | Resultado |
|---|---|
| `01-problema-original.json` | Base sin modificar: saldos 1000/500 → 800/500, tabla transferencia vacía. |
| `02-transferencia-exitosa.json` | Transferencia 2, 201, `COMPLETADA`, saldos 800/700. |
| `03-compensacion.json` | Transferencia 1, 409, `COMPENSADA`, saldos 1000/500 y dos asientos de cargo/devolución. |
| `04-compensacion-pendiente.json` | Transferencia 3, 202, estado pendiente conservado tras reiniciar. |
| `05-circuit-breaker.json` | Apertura tras tres fallas; tres rechazos sin llamadas adicionales; semiabierto y cierre al recuperar cuenta. |
| `08-tests.txt` | 25 pruebas Java aprobadas. |

Las capturas en `evidencia/capturas/` muestran vistas renderizadas de estas respuestas y consultas reales. Las marcas `Z` de las aplicaciones están en UTC; `fecha_local` está en Ciudad de México.

Para repetir los experimentos, copia también `evidencia/` al directorio raíz del clon de Mexi Banco. El script usa cuentas reservadas terminadas en 8101/8102, 8201/8202, 8203/8204 y 8301/8302. Debe ejecutarse en un entorno de prueba nuevo: falla si ya existen, en lugar de sobrescribir datos.

1. Antes de copiar el código modificado, levanta la base oficial con `PAUSA_ENTRE_CARGO_Y_ABONO_MS=20000 docker compose -f docker-compose.yml -f evidencia/compose-lab.yml up --build -d --wait` y ejecuta `python3 evidencia/experimentos.py base`.
2. Copia los archivos de `codigo/`. Ejecuta `docker compose -f docker-compose.yml -f evidencia/compose-lab.yml up --build -d --wait transferencia gateway` para reconstruir transferencia y recrear gateway con su timeout.
3. Ejecuta, en ese orden, `python3 evidencia/experimentos.py sagas`, `python3 evidencia/experimentos.py pendiente` y `python3 evidencia/experimentos.py circuito`.

El script usa `docker-compose.yml` más `evidencia/compose-lab.yml` para mantener una réplica y el proyecto aislado. Detiene y enciende **solo cuenta de S08**. En las pruebas pendientes se consulta PostgreSQL para comprobar el estado persistido; la aplicación no accede a bases ajenas.

## Pruebas automáticas

Desde el clon con los cambios copiados:

```bash
docker build -f transferencia/Dockerfile.tests -t mexi-banco-s08-tests transferencia
```

O, con Java 21 instalado, dentro de `transferencia/`: `./mvnw test`.

Las 25 pruebas incluyen compensación, saldo insuficiente, timeouts ambiguos, errores posteriores al abono, fallo del ledger después de devolver, notificaciones fallidas, concurrencia del circuito y balanceo por nombre. En semiabierto se permite un solo intento; una respuesta antigua no puede cerrar un circuito abierto por otra llamada.

## Idempotencia y límites

La API original de cuenta no tiene una llave idempotente para cargos y abonos. Por ello no se reintenta automáticamente una mutación ni se devuelve dinero después de un timeout ambiguo. Tampoco se deduplica el `POST /transferencias`: repetirlo es otra operación.

Para automatizar una compensación pendiente se necesita una llave `idSaga:compensacion`, registrada atómicamente con el saldo en cuenta, una respuesta reutilizable y un trabajador con reintentos y espera creciente. Si el resultado es incierto se debe conciliar primero. Un simple `if` en el orquestador no resuelve la caída entre el commit remoto y el guardado local.

Esta entrega conserva el mínimo solicitado: devolución comprobada, movimientos, estado pendiente durable y explicación de recuperación. No implementa Outbox, recuperación automática tras caídas ni atomicidad entre saldo y ledger. La saga tampoco ofrece aislamiento global: durante la pausa se observa el saldo reducido.

## Fuentes

- [Enunciado del Lab S08](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s08-saga-circuit-breaker/README.md).
- [Material de la semana 08](https://github.com/OscarRuiz21/sd-2027-1/blob/main/material/S08/S08-Clase-8-SD.pdf).
- [Fowler: CircuitBreaker](https://martinfowler.com/bliki/CircuitBreaker.html).
- [Richardson: Pattern Saga](https://microservices.io/patterns/data/saga.html).
