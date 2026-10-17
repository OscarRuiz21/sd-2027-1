# Lab S08 - saga orquestada y circuit breaker

La entrega solicitada esta en `bitacora-s08.pdf`, `codigo/transferencia/` y
`codigo/docker-compose.yml`. El clon `mexi-banco-s08/` es solo un entorno local
de pruebas y esta ignorado por Git.

## Como ejecutar la entrega

Parte de la rama `03-gateway-discovery-balanceo` de Mexi Banco. Copia el contenido
de `codigo/transferencia/` sobre `transferencia/` de ese clon y reemplaza su
`docker-compose.yml` con el de `codigo/`. En el clon:

```bash
docker compose up --build -d --scale cuenta=1
docker compose ps
```

Para la falla simulada de un abono, crea dos cuentas por el gateway y usa la CLABE
de destino al recrear `transferencia`:

```bash
FALLAR_ABONO_CLABE=CLABE_DESTINO docker compose up -d --no-deps --force-recreate transferencia
```

El POST a `/api/transferencias` responde `409` con `estado: COMPENSADA`, la causa
en `detalle` y el ID para consultar `/api/transferencias/{id}`. En la base de
`transferencia` se guarda cada transicion. La devolucion aparece en movimientos
de origen como `TRANSFERENCIA_RECIBIDA` con referencia
`COMPENSACION transferencia #ID` (el enum original de `movimiento` no tiene un
tipo especifico para devoluciones).

Para repetir la caida de `cuenta` durante la pausa:

```bash
PAUSA_ENTRE_CARGO_Y_ABONO_MS=20000 FALLAR_ABONO_CLABE= docker compose up -d --no-deps --force-recreate transferencia
docker compose stop cuenta
docker compose up -d --no-deps --scale cuenta=1 cuenta
```

Deten `cuenta` **solo despues de observar el cargo**. El gateway puede responder
`504` antes de que pasen los 20 segundos de pausa: su timeout es de 15 segundos,
pero la saga continua en el servicio y acaba en `COMPENSACION_PENDIENTE`.
Consulta la base o el endpoint GET para ver el estado final.

El circuit breaker es manual: abre tras tres fallas de disponibilidad, espera
ocho segundos y admite una sola llamada de prueba en `HALF_OPEN`. Los cambios
se leen en `docker compose logs transferencia` con el prefijo `CIRCUITO cuenta`.
Los timeouts de conexion y lectura de HTTP son 2 y 5 segundos.

Como punto opcional, la devolucion hace hasta tres intentos con espera de 250 y
500 ms **solo** si el circuito rechazo localmente la llamada o si Eureka no
encontro ninguna instancia: en ambos casos no hubo POST. Un timeout o 5xx de
un POST enviado no se reintenta automaticamente.

**Limite consciente:** `cuenta` no acepta claves de idempotencia en `/abonos`.
Si una devolucion da timeout, pudo haberse aplicado aunque el orquestador no
recibiera respuesta. Por eso `COMPENSACION_PENDIENTE` requiere conciliacion
manual del saldo y movimientos antes de cualquier reintento. Automatizarlo sin
ampliar la API de `cuenta` podria devolver el dinero dos veces.

Las pruebas unitarias se ejecutaron con Maven (`./mvnw test`). Los valores y
estados observados en Docker estan documentados en la bitacora. No se hizo
commit ni push.
