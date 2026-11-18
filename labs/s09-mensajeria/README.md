# Lab S09 · Que el aviso no tumbe la transferencia

**Sistemas Distribuidos 2027-1 · Grupo 2 · S09 · sábado 17 de octubre**
**Entrega: sábado 24 de octubre de 2026, antes de las 07:00** (antes de la clase de la S10)

## Objetivo

Al terminar, la transferencia de Mexi Banco **avisa con un evento en RabbitMQ** en lugar de llamar
por HTTP a `notificacion`, y tu consumidor **sobrevive a una caída, a un duplicado y a un mensaje
envenenado** sin perder ni repetir avisos.

## Contexto

En la S08 la transferencia se volvió una saga: si el abono falla, el dinero regresa. Pero queda un
cabo suelto, el mismo de la portada de la clase de hoy: al final, la saga **llama por HTTP** a
`notificacion` y **espera su respuesta**. Si `notificacion` está caído, una transferencia que ya
movió el dinero responde con error por culpa de un aviso.

Hoy vimos la salida: poner un broker en medio. La transferencia **publica un hecho**
(`TransferenciaRealizada`) y sigue con lo suyo; `notificacion` lo lee **cuando puede**. Es la
diferencia entre una llamada telefónica y dejar un recado en el buzón.

Pero el buzón trae sus propios problemas, y también los vimos: el mensaje puede llegar **dos
veces** (at-least-once) y puede llegar un mensaje que **nunca** se va a poder procesar (poison
pill). En este lab los provocas y los resuelves.

## Antes de empezar

El punto de partida es la **solución del lab S08** más el broker ya instalado, en la rama
`05-mensajeria-base`:

```bash
git clone --branch 05-mensajeria-base https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s09
cd mexi-banco-s09
docker compose up --build -d
docker compose ps        # espera a que todos digan healthy
```

Si ya tienes el repo: `git fetch origin` y `git switch 05-mensajeria-base`.

- El panel de RabbitMQ está en http://localhost:15672 (usuario `mexibanco`, contraseña `mexibanco`).
- Si tu laptop sufre, baja la carga: `docker compose up -d --scale cuenta=1` y `docker compose stop spei` (este lab no usa spei).
- Si el puerto 8080 de tu máquina está ocupado: `PUERTO_GATEWAY=18080 docker compose up -d`.

**Cómo sabes que el base corre:** una transferencia por el gateway responde `201` y su estado es
`COMPLETADA`. El README de la rama trae los comandos.

## El contrato del evento

Usa **exactamente** estos nombres. Así cualquier compañero puede conectar otro consumidor a tu
broker, y así se revisa tu entrega.

| Pieza | Valor |
|---|---|
| Exchange | `transferencias`, tipo **topic**, durable |
| Routing keys | `transferencia.realizada` (la saga llega a `COMPLETADA`) · `transferencia.revertida` (la saga llega a `COMPENSADA`) |
| Cola de notificacion | `notificacion.transferencias`, durable, enlazada con el patrón `transferencia.*` |
| Dead letter exchange | `transferencias.dlx`, tipo direct |
| Dead letter queue | `notificacion.transferencias.dlq`, enlazada con la clave `notificacion.transferencias` |

El evento, en JSON:

```json
{
  "eventoId": "8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c",
  "tipo": "TransferenciaRealizada",
  "transferenciaId": 42,
  "clabeOrigen": "002180000000000001",
  "clabeDestino": "002180000000000002",
  "monto": 200.00,
  "saldoOrigen": 800.00,
  "saldoDestino": 700.00,
  "fecha": "2026-10-17T10:42:07Z"
}
```

`eventoId` lo genera la transferencia y **no cambia si el evento se vuelve a publicar**: es tu llave
de idempotencia.

## Parte 0 · Ver el problema (≈10 min)

Antes de tocar código, reprodúcelo y déjalo en tu bitácora:

1. Apaga `notificacion`: `docker compose stop notificacion`.
2. Haz una transferencia por el gateway.
3. Consulta su estado y los saldos: **el dinero se movió, pero la transferencia se quedó en
   `ABONADA` y te respondió con error**. Explica en una frase por qué.
4. Vuelve a encender `notificacion`.

## Parte 1 · Publicar el evento (≈25 min, guiada)

En `transferencia`, busca los comentarios `// LAB S09:`. Ahí es donde hoy se llama a
`NotificacionCliente`.

1. Declara el exchange `transferencias` (topic, durable) como un `@Bean`.
2. Configura un `RabbitTemplate` con un convertidor JSON (`Jackson2JsonMessageConverter`).
3. Crea el record del evento con los campos del contrato.
4. Cuando la saga llegue a `COMPLETADA`, publica `TransferenciaRealizada` con la clave
   `transferencia.realizada`. Cuando llegue a `COMPENSADA`, publica `TransferenciaRevertida` con
   `transferencia.revertida`.
5. **Quita la llamada HTTP a `notificacion`** de la transferencia. Spei puede seguir como está.

**Terminado cuando:** con `notificacion` **apagado**, la transferencia responde `201`, queda en
`COMPLETADA` y el exchange aparece en el panel. Todavía no hay cola, así que el mensaje se pierde:
anótalo, porque en la Parte 2 lo arreglas.

> Pista para la bitácora: activa los *publisher confirms*
> (`spring.rabbitmq.publisher-confirm-type`). ¿Qué te garantiza la confirmación y qué **no** te
> garantiza?

## Parte 2 · Consumir el evento (≈25 min)

En `notificacion` ya hay un esqueleto, `mensajeria/ConsumidorDeTransferencias.java`, marcado con
`// LAB S09:`:

1. Declara la cola `notificacion.transferencias` y su binding con el patrón `transferencia.*`.
2. Escribe un `@RabbitListener` que, por cada evento, guarde los avisos en la bandeja:
   - **dos** por `TransferenciaRealizada`: uno a la cuenta de origen y otro a la de destino;
   - **uno** por `TransferenciaRevertida`: a la cuenta de origen ("tu transferencia no se realizó y
     te devolvimos el dinero").
3. Usa **ack manual** y haz el ack **después** de guardar.

**Terminado cuando** puedas mostrar esto con capturas:

1. Apagas `notificacion` y haces **tres** transferencias.
2. En el panel, `notificacion.transferencias` tiene **3 mensajes Ready**.
3. Enciendes `notificacion`: la cola baja a 0 y la bandeja
   (`GET /api/notificaciones?clabe=...`) tiene los avisos.

Eso es el **desacople en tiempo**: el productor y el consumidor ya no tienen que estar vivos al
mismo tiempo.

Prueba también una transferencia con la falla provocada de la S08 (`FALLAR_ABONO_A_CLABE`) y
muestra que llega el aviso de `TransferenciaRevertida`.

## Parte 3 · Rompe algo: el duplicado (≈20 min)

La mensajería entrega **al menos una vez**. Provócalo de una de estas dos formas:

- **Desde el panel:** en la cola, *Get messages* con *Nack message requeue true* para copiar un
  mensaje; luego, en el exchange `transferencias`, *Publish message* con la misma clave y el mismo
  cuerpo (mismo `eventoId`).
- **Matando el consumidor:** agrega una pausa configurable entre guardar y hacer ack, apaga
  `notificacion` durante la pausa y vuélvelo a encender. El mensaje regresa con `redelivered`.

Primero muestra el problema: **avisos repetidos** en la bandeja. Después hazlo **idempotente**:
guarda el `eventoId` con una restricción `UNIQUE`, **en la misma transacción** que los avisos, y si
ya existía, no hagas nada y confirma el mensaje.

**Terminado cuando:** repites el experimento y la bandeja tiene **un solo juego** de avisos.

## Parte 4 · Rompe algo: el mensaje envenenado (≈20 min)

Publica desde el panel, en el exchange `transferencias` con la clave `transferencia.realizada`, un
cuerpo que no sea un evento válido (por ejemplo `{"hola": `).

1. Muestra qué pasa **antes** de arreglarlo: el mensaje rebota, se queda trabado o tira el
   consumidor. Descríbelo con los logs.
2. Configura la **DLQ**: la cola principal con `x-dead-letter-exchange` y
   `x-dead-letter-routing-key`, el DLX y la DLQ del contrato, y en el consumidor un **nack sin
   reencolar** cuando el mensaje no se pueda leer.

> Ojo: RabbitMQ no deja cambiar los argumentos de una cola que ya existe
> (`PRECONDITION_FAILED`). Si ya la habías creado sin DLX, bórrala desde el panel antes de
> reiniciar.

**Terminado cuando:** el mensaje malo aparece en `notificacion.transferencias.dlq` y las
transferencias normales siguen generando avisos.

## Reto (opcional, suma puntos) · Outbox

¿Qué pasa si **RabbitMQ** es el que está caído cuando la saga llega a `COMPLETADA`? Pruébalo:
`docker compose stop rabbitmq`. Es la **doble escritura** de la clase: la base dice que la
transferencia se completó y el evento nunca sale.

Arréglalo con un **Transactional Outbox**:

- una tabla `outbox` en la base de `transferencia`, escrita **en la misma transacción** que el
  cambio de estado final;
- un relay `@Scheduled` que publica las filas pendientes y las marca;
- `SELECT ... FOR UPDATE SKIP LOCKED` si corres más de una réplica de `transferencia`.

**Terminado cuando:** con RabbitMQ apagado, la transferencia se completa; al encenderlo, el evento
sale solo y llega el aviso.

## Cómo se trabaja

Con tu **equipo del proyecto**: se apoyan, se reparten la investigación y se ayudan a depurar.
Pero **cada quien entrega lo suyo**: su código y su bitácora.

## Qué entregas

En tu rama `entregas_apellido_nombre` del repo del grupo:

```
entregas/apellido_nombre/s09/
├── bitacora-s09.pdf
└── codigo/
    ├── transferencia/        ← la carpeta completa del servicio, SIN target/
    ├── notificacion/         ← la carpeta completa del servicio, SIN target/
    └── docker-compose.yml    ← solo si lo cambiaste
```

**La bitácora**, en PDF y con capturas en cada parte:

1. **El problema** (Parte 0): estado y saldos, y en una frase por qué pasó.
2. **Publicar**: el código clave explicado con tus palabras, y qué garantiza el *publisher confirm*.
3. **Desacople en tiempo**: las tres transferencias con `notificacion` apagado, la cola con 3
   Ready y la bandeja al volver.
4. **El duplicado**: los avisos repetidos, tu arreglo y el experimento repetido sin duplicado.
5. **La DLQ**: el mensaje malo antes y después, con el panel.
6. **Preguntas de cierre** (abajo).
7. **El Outbox**, si hiciste el reto.

```bash
git checkout entregas_apellido_nombre
git pull origin main
mkdir -p entregas/apellido_nombre/s09/codigo
# ... copia transferencia/ y notificacion/ (sin target/) y tu bitacora-s09.pdf ...
git add entregas/apellido_nombre/s09
git commit -m "Lab S09: mensajería con RabbitMQ"
git push
```

**No abras pull request**: el push ES la entrega. Tarde no baja puntos, pero queda registrado en tu
tendencia (ver [`ENTREGAS.md`](../../ENTREGAS.md)).

## Preguntas de cierre

Respóndelas en la bitácora, en un párrafo cada una:

1. Si **RabbitMQ** está caído cuando la saga llega a `COMPLETADA`, ¿qué pasa con el aviso en tu
   versión? ¿Por qué no lo arregla el *publisher confirm*?
2. ¿Por qué el ack va **después** de guardar y no antes? ¿Qué garantía obtienes con cada orden?
3. **Elige y di qué sacrificaste:** al quitar la llamada síncrona ganaste que el aviso no tumbe la
   transferencia. ¿Qué perdiste? Tres líneas: elección, porque, sacrifico.

## Si te atoras

- Si solo cambias un servicio, reconstruye solo ese: `docker compose up --build -d notificacion`.
- `PRECONDITION_FAILED` al arrancar: la cola ya existe con otros argumentos. Bórrala desde el
  panel (pestaña *Queues and Streams*) y reinicia el servicio.
- El panel te deja ver un mensaje sin consumirlo: *Get messages* con *Nack message requeue true*.
- Dudas o errores: abre un Issue en el repo del grupo con el error completo y el comando que
  corriste.
