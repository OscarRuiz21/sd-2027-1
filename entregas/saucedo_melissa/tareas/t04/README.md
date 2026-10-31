# T04 · ¿Qué reloj usan las tecnologías que conoces?

**Alejandra Melissa Saucedo Serralde · Sistemas Distribuidos 2027-1 · Grupo 2**

Elegí tres tecnologías: **PostgreSQL** y **RabbitMQ**, porque son las que usamos en Mexi Banco, y **Kafka**, porque es la que más se compara con RabbitMQ y deja ver bien la diferencia entre "orden por reloj" y "orden por posición". Para cada una respondo las cuatro preguntas de la tarea y al final hay una tabla resumen, el opcional del reloj de Lamport y las fuentes.

---

## 1. PostgreSQL

**¿Qué es y para qué se usa?** Es una base de datos relacional de código abierto. En Mexi Banco cada servicio (`cuenta`, `movimiento`, `transferencia`, `notificacion`) guarda lo suyo en su propia base, dentro del mismo Postgres: saldos, movimientos y el estado de cada transferencia.

**¿Qué tiempo usa para ordenar?** Hay que separar dos cosas:

- **Para poner horas en los datos usa un reloj físico, el del servidor.** `now()` (igual a `transaction_timestamp()`) devuelve la hora de **inicio de la transacción** y no cambia mientras la transacción dura. `statement_timestamp()` devuelve la hora en que llegó la sentencia actual. `clock_timestamp()` devuelve la hora real en ese instante y sí cambia, incluso dentro de una misma sentencia.
- **Para decidir qué datos ve cada consulta no usa la hora.** Usa MVCC: cada sentencia ve un *snapshot*, una versión de los datos "como estaban hace un momento", sin importar lo que otras transacciones estén modificando. La página de MVCC que revisé no menciona relojes ni timestamps.

**¿Qué orden garantiza y hasta dónde?** Dentro de una instancia de Postgres, cada consulta ve una versión consistente de los datos (aislamiento). Lo que **no** me da es ordenar por `now()`: como `now()` es la hora de *inicio* de la transacción, una transacción que empezó antes puede terminar después. Para eso existe la hora de *commit*, pero es opcional: `pg_xact_commit_timestamp()` solo da datos útiles si se activa `track_commit_timestamp`, solo para las transacciones confirmadas después de activarla, y el vacuum la va borrando. Lo que revisé es de una sola instancia; no revisé réplicas.

**¿Qué pasa cuando los relojes no coinciden?** Postgres no los sincroniza: las funciones leen la hora de la máquina donde corre, y la documentación que revisé no menciona NTP. Si dos servidores tienen horas distintas, sus `now()` no son comparables. Por eso en Mexi Banco no ordenamos una transferencia por su `fecha`: la saga la ordena por **estados** (`INICIADA` → `CARGADA` → `COMPLETADA`, o `COMPENSADA`), que es un orden lógico guardado en la base.

---

## 2. RabbitMQ

**¿Qué es y para qué se usa?** Es un *broker* de mensajes: un buzón entre servicios. En el lab S09 la transferencia deja un recado (`TransferenciaRealizada`) en el exchange `transferencias`, y `notificacion` lo lee de su cola `notificacion.transferencias` cuando puede, aunque no estuviera encendida en ese momento.

**¿Qué tiempo usa para ordenar?** **Orden de llegada a la cola**, no un reloj. La documentación dice que las colas son colecciones ordenadas de mensajes, con comportamiento FIFO (salvo las colas con prioridad). Los mensajes sí pueden traer un campo `timestamp`, pero es una marca de tiempo proporcionada por la aplicación: la pone quien publica. En las páginas que revisé no aparece que RabbitMQ la use para ordenar.

**¿Qué orden garantiza y hasta dónde?** **FIFO dentro de una cola**, con matices importantes. La documentación dice que el orden puede verse afectado por varios consumidores compitiendo por la misma cola, por prioridades de consumidor y por **redeliveries** de cualquier tipo (por ejemplo al cerrarse el canal o tras un `nack`), y que en las colas con prioridad el FIFO no está garantizado por definición. Sobre el orden entre colas distintas no encontré ninguna garantía. Esto lo viví en el lab S09: si `notificacion` muere después de guardar y antes del `ack`, el mensaje vuelve a `Ready` y se entrega otra vez con `redelivered=true`, o sea repetido y posiblemente fuera de turno.

**¿Qué pasa cuando los relojes no coinciden?** Casi nada, porque el orden lo da el broker al encolar y no el reloj de quien publica. Si el reloj de un servicio va atrasado, solo sale mal la etiqueta `timestamp` que él mismo puso; no encontré que el broker la corrija, la valide o la use para desempatar. RabbitMQ no sincroniza ni espera por la hora.

---

## 3. Kafka

**¿Qué es y para qué se usa?** Es una plataforma de *streaming* de eventos basada en un log distribuido. Los mensajes se guardan en *topics*, y cada topic se divide en **particiones**. Se usa para mover y guardar grandes flujos de eventos que varios consumidores pueden leer, y releer, a su propio ritmo.

**¿Qué tiempo usa para ordenar?** Un **número de secuencia, el *offset***: la posición del mensaje dentro de su partición. La documentación lo presenta como la posición del mensaje en el log de la partición, y dice que es permanente: nunca cambia. Además, cada mensaje lleva un *timestamp*, que por omisión es `CreateTime` (la hora de creación del mensaje, que pone el productor) y que se puede configurar como `LogAppendTime` (la hora en que el broker lo escribe). El timestamp es información; el orden lo da el offset.

**¿Qué orden garantiza y hasta dónde?** **Orden total dentro de una partición.** La documentación describe el topic como un conjunto de particiones totalmente ordenadas. Como los mensajes con la misma llave caen en la misma partición, también hay **orden por llave** (por ejemplo, todo lo de un mismo usuario). **Entre particiones distintas no hay garantía de orden.** La comparación oficial con RabbitMQ añade que cada partición va a lo más a un consumidor del grupo.

**¿Qué pasa cuando los relojes no coinciden?** Con `CreateTime` el broker **compara** el timestamp del mensaje con su propia hora y lo **rechaza** si la diferencia se pasa de los límites configurados: `message.timestamp.after.max.ms` (por omisión, 1 hora hacia el futuro) y `message.timestamp.before.max.ms` (por omisión no hay límite práctico hacia el pasado). Con `LogAppendTime` esos límites se ignoran, porque la hora la pone el broker. En ningún caso sincroniza relojes ni espera: acepta, rechaza o usa su propia hora, y el orden siempre lo decide el offset.

---

## Resumen

| Tecnología | ¿Qué usa para ordenar? | ¿Hasta dónde llega el orden? | Si los relojes no coinciden |
|---|---|---|---|
| **PostgreSQL** | Hora del servidor para las marcas (`now()`, `clock_timestamp()`); snapshots (MVCC) para decidir qué se ve | Una instancia: cada consulta ve una versión consistente. `now()` no ordena commits | No los sincroniza; las horas de servidores distintos no son comparables |
| **RabbitMQ** | Orden de llegada a la cola (FIFO) | Dentro de una cola; se puede alterar con varios consumidores, prioridades y redeliveries | Casi no le afecta: el orden lo pone el broker; el `timestamp` es de la aplicación y no se corrige |
| **Kafka** | Offset (número de secuencia) por partición; timestamp solo informativo | Dentro de una partición y por llave; no entre particiones | Con `CreateTime` rechaza timestamps muy desfasados; con `LogAppendTime` usa su hora |

En las tres, el orden "de verdad" no sale del reloj de pared: sale de una **posición** (cola, offset) o de un **estado/versión** (snapshot, estados de la saga), y la hora queda como dato extra. Eso es justo lo que vimos en clase: el reloj de pared no sirve para ordenar, porque NTP acerca los relojes pero nunca los iguala.

---

## Opcional: un reloj de Lamport en Java

Está en [`lamport/Lamport.java`](lamport/Lamport.java) y su salida real en [`lamport/salida.txt`](lamport/salida.txt). Simula **3 servidores (A, B y C)** que se mandan **5 mensajes**. Cada uno lleva su reloj en un `AtomicLong` y usa las reglas de Lamport:

- **evento local:** `reloj = reloj + 1`
- **enviar:** `reloj = reloj + 1`, y el mensaje viaja con ese valor
- **recibir:** `reloj = max(reloj local, reloj del mensaje) + 1`

Para correrlo (usé Java 21, el mismo del proyecto):

```bash
cd lamport
javac Lamport.java
java Lamport
```

Parte de la salida (la completa está en `salida.txt`):

```
[A | reloj= 1] arranca
[A | reloj= 2] envia 'm1: pedido' a B
[B | reloj= 3] recibe 'm1: pedido' de A (traia 2)
[B | reloj= 4] guarda el pedido
[C | reloj= 1] lee su disco
[B | reloj= 5] envia 'm2: confirmacion' a C
[C | reloj= 6] recibe 'm2: confirmacion' de B (traia 5)
...
Mensajes: 5. Con reloj de envio < reloj de recepcion: 5. Causalidad OK
```

**Por qué la salida respeta la causalidad.** Si un evento *causó* a otro (porque pasaron en el mismo servidor, o uno es el envío y el otro la recepción de ese mensaje), el primero siempre queda con un reloj **menor**:

- En un mismo servidor el reloj solo sube, nunca baja.
- Al recibir, el reloj queda en `max(local, recibido) + 1`, que siempre es **mayor** que el valor con el que el mensaje salió. Por eso en los 5 mensajes el reloj de envío es menor que el de recepción (el programa lo comprueba y lo imprime).
- Como las flechas se encadenan, el orden se mantiene a distancia: `A arranca (1)` → `A envía m1 (2)` → `B recibe m1 (3)` → `B envía m2 (5)` → `C recibe m2 (6)`. Todo lo que C hace después de recibir m2 tiene un reloj mayor que `arranca` de A, aunque A y C nunca se hablaron directo.

**Lo que Lamport no dice.** Solo funciona en una dirección: si A causó a B, entonces `reloj(A) < reloj(B)`, pero **no al revés**. En la salida, `C lee su disco` tiene reloj 1 y `A envía m1` tiene reloj 2, pero entre ellos no hay ninguna relación: C no sabe nada de A. Además `A arranca` y `C lee su disco` **empatan en 1**, y para ordenarlos por completo tuve que desempatar por nombre. Con un reloj de Lamport no puedo saber si dos eventos fueron concurrentes; para eso hacen falta **relojes vectoriales**.

---

## Fuentes

PostgreSQL
- [9.9. Date/Time Functions and Operators (`now()`, `statement_timestamp()`, `clock_timestamp()`)](https://www.postgresql.org/docs/current/functions-datetime.html)
- [13.1. Introduction (MVCC y snapshots)](https://www.postgresql.org/docs/current/mvcc-intro.html)
- [9.27. System Information Functions (hora de commit, `track_commit_timestamp`)](https://www.postgresql.org/docs/current/functions-info.html)

RabbitMQ
- [Queues (orden FIFO, redeliveries, consumidores)](https://www.rabbitmq.com/docs/4.1/queues)
- [Publishers (propiedades del mensaje, `timestamp`)](https://www.rabbitmq.com/docs/publishers)
- [RabbitMQ vs. Apache Kafka](https://www.rabbitmq.com/docs/4.3/compare/kafka)

Kafka
- [Design (particiones, orden, offsets)](https://kafka.apache.org/35/design/design/)
- [Topic Configs (`message.timestamp.type`, `message.timestamp.after.max.ms`, `message.timestamp.before.max.ms`)](https://kafka.apache.org/44/configuration/topic-configs/)

Clase y laboratorios
- Clase sobre relojes físicos, NTP, Lamport y relojes vectoriales (S10).
- Lab S09 de Mexi Banco (RabbitMQ) y lab S08 (saga).

Me apoyé en IA para buscar las páginas y armar el borrador, y revisé cada afirmación contra las fuentes de arriba.
