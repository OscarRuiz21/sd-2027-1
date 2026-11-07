# T04 · ¿Qué reloj usan las tecnologías que conoces?

Elegí tres que ordenan de forma muy distinta: **PostgreSQL**, que ya usamos en todo Mexi Banco, **Kafka**, que es el broker de log que vimos en la lectura de Kreps, y **CockroachDB**, que es una base distribuida y tiene que resolver el problema de los relojes de verdad. Lo interesante es que ninguna de las tres usa el reloj de pared para decidir qué pasó primero, cada una lo resuelve a su manera.

## 1. PostgreSQL

**Qué es.** Es una base de datos relacional, la que guarda las cuentas, los movimientos y las transferencias en Mexi Banco, se usa para datos donde importa que las transacciones sean ACID, o sea que el cargo y el abono pasen juntos o no pasen.

**Qué tiempo usa para ordenar.** Un **número de secuencia que asigna un solo servidor**, no un reloj. En realidad usa dos:

- El **ID de transacción** (`xid`), un contador de 32 bits que el servidor le va dando a cada transacción que escribe, en orden, y es lo que usa MVCC para saber qué versión de una fila ve cada transacción, cada fila guarda el `xid` que la creó (`xmin`) y el que la borró (`xmax`), y una transacción solo ve las filas cuyas transacciones ya estaban confirmadas cuando tomó su *snapshot*.
- El **LSN** (*Log Sequence Number*), que es la posición en bytes dentro del WAL, el log donde se escribe cada cambio antes de tocar las tablas, el WAL es un solo log y cada registro tiene un LSN más grande que el anterior, así que el orden de los cambios es el orden del WAL, y las réplicas aplican el WAL en ese mismo orden.

El reloj de pared solo aparece como dato, por ejemplo `now()`, que regresa la hora en que **empezó** la transacción (no la hora real de cada sentencia), y `track_commit_timestamp`, que si lo activas guarda a qué hora se confirmó cada transacción, pero nada de eso se usa para decidir el orden.

**Por qué ese y no otro.** Porque en PostgreSQL todas las escrituras pasan por un solo servidor (el primario), y si hay un solo proceso que decide, un contador es suficiente y es exacto, no hace falta Lamport porque no hay dos máquinas que tengan que ponerse de acuerdo.

**Qué orden te garantiza y hasta dónde.** Un orden total de todas las transacciones del servidor primario, dentro de **ese** servidor, las réplicas ven exactamente el mismo orden porque aplican el mismo WAL, pero pueden ir atrás (si la réplica es asíncrona, lees algo viejo, en el mismo orden pero viejo). Entre dos servidores de Postgres distintos no hay ningún orden, por ejemplo, en Mexi Banco la base `cuenta` y la base `transferencia` tienen cada una su propio WAL y sus propios `xid`, y no hay forma de saber con eso cuál cambio fue antes.

**Qué pasa cuando los relojes no coinciden.** Para el orden, nada, porque no los usa, si el reloj del servidor se atrasa o se adelanta, el `xid` y el LSN siguen creciendo igual, lo único que se ve mal son las columnas con `now()`: una réplica promovida con el reloj atrasado puede guardar una fecha *anterior* a la del último movimiento, y si ordenas el estado de cuenta por `fecha` sale desordenado aunque la base sepa perfectamente el orden real. Por eso en Mexi Banco ordenar por `id` (que sale de una secuencia) es más seguro que ordenar por fecha. Lo que sí hace es que el `xid` de 32 bits se da la vuelta (*wraparound*) después de unos 4 mil millones de transacciones, y por eso existe el `VACUUM` que "congela" las filas viejas.

## 2. Apache Kafka

**Qué es.** Es un log distribuido, los productores escriben mensajes al final de un *topic* y los consumidores los leen en orden, cada uno a su ritmo, y cada *topic* se parte en **particiones** que viven en distintos brokers. Se usa para mover eventos entre servicios, para guardar el historial de cambios y para procesar flujos de datos.

**Qué tiempo usa para ordenar.** El **offset**, un número de secuencia que asigna el **líder de cada partición** cuando agrega el mensaje a su log, empieza en 0 y va subiendo de uno en uno, es como el LSN de Postgres pero por partición. Además cada mensaje trae un *timestamp* de reloj de pared, que puede ser `CreateTime` (la hora del productor) o `LogAppendTime` (la hora del broker al escribirlo, según `message.timestamp.type`), pero ese timestamp es un dato, sirve para buscar mensajes por hora o para borrar los viejos por retención, no para el orden. Para saber quién es el líder legítimo usa la **época del líder** (*leader epoch*), otro número que sube cada vez que cambia el líder, y es lo que evita que un líder viejo que regresa escriba encima de lo que ya decidió el nuevo.

**Por qué ese y no otro.** Porque cada partición tiene un solo líder que escribe, igual que el primario de Postgres, así que un contador basta, el truco de Kafka es que no intenta tener un orden de todo el sistema, que sería muy caro, sino uno por partición, y así puede tener muchas particiones escribiendo en paralelo en distintas máquinas sin coordinarse entre ellas.

**Qué orden te garantiza y hasta dónde.** Orden total **dentro de una partición**, todos los consumidores de esa partición leen los mensajes exactamente en el mismo orden en que se escribieron, y entre particiones **no hay ningún orden**. Por eso se elige la llave del mensaje con cuidado: los mensajes con la misma llave van a la misma partición, entonces si la llave es la CLABE, todos los movimientos de una cuenta salen en orden, pero los de dos cuentas distintas no. Dentro de un mismo productor, con el productor idempotente, cada mensaje lleva también un número de secuencia del productor, y el broker lo usa para descartar duplicados cuando el productor reintenta.

**Qué pasa cuando los relojes no coinciden.** Para el orden, nada, el offset no depende del reloj, pero el *timestamp* sí puede salir raro, con `CreateTime` cada productor pone la hora de su máquina, así que un mensaje con offset 100 puede tener una hora *anterior* al de offset 99 si su productor tenía el reloj atrasado, Kafka lo deja pasar tal cual (solo rechaza diferencias enormes, según la configuración del broker), y si eso importa, se usa `LogAppendTime` para que la hora la ponga el broker. La retención por tiempo y las búsquedas por hora usan ese timestamp, así que un reloj muy adelantado puede hacer que un segmento se tarde más en borrarse.

## 3. CockroachDB

**Qué es.** Es una base de datos SQL distribuida (habla el protocolo de PostgreSQL), reparte los datos en rangos y cada rango se replica en varios nodos con Raft, se usa cuando quieres SQL con transacciones pero repartido en varias máquinas o regiones, y que sobreviva a que se caiga un nodo.

**Qué tiempo usa para ordenar.** Un **reloj híbrido**, el *Hybrid Logical Clock* (HLC), cada timestamp tiene dos partes, una **física**, que siempre está muy cerca de la hora de pared del nodo, y una **lógica**, un contador que desempata eventos con la misma parte física. Funciona como Lamport: cuando un nodo recibe un mensaje con un HLC mayor que el suyo, adelanta su reloj a ese valor, así que el HLC de un nodo nunca va para atrás y siempre es mayor o igual a su hora de pared. El nodo que recibe la transacción (el *gateway*) le pone un timestamp HLC, y ese timestamp se usa para las versiones de cada valor (MVCC) y para decidir qué ve cada transacción.

**Por qué ese y no otro.** Porque aquí sí hay muchos nodos escribiendo al mismo tiempo y no hay un solo contador central, un contador central sería un cuello de botella, el reloj de pared solo no sirve porque NTP nunca deja los relojes iguales, y Lamport solo tampoco, porque un reloj puramente lógico no tiene nada que ver con la hora real y no podrías pedir "lee los datos de hace 10 segundos". El HLC junta las dos cosas, respeta la causalidad como Lamport y además se parece a la hora real. Google Spanner resuelve lo mismo con TrueTime y relojes atómicos, CockroachDB lo hace sin hardware especial y por eso necesita lo que sigue.

**Qué orden te garantiza y hasta dónde.** Transacciones **serializables en todo el clúster**, el resultado es como si se hubieran ejecutado una por una en el orden de sus timestamps HLC, aunque toquen datos en distintos nodos. Lo que **no** garantiza del todo es la linealizabilidad entre transacciones que no se comunican: si dos clientes escriben en dos nodos con relojes desfasados, el orden de sus timestamps puede no coincidir con el orden en el que de verdad pasaron, y la documentación dice que eso se cuida solo mientras los relojes estén dentro del desfase máximo.

**Qué pasa cuando los relojes no coinciden.** Aquí es donde más se nota, porque CockroachDB **depende** de que los relojes estén más o menos cerca:

- Hay un **desfase máximo** configurado (`--max-offset`, 500 ms por omisión), y cada nodo mide el desfase con los demás, si un nodo detecta que su reloj está fuera de sincronía con al menos la mitad del clúster por más del 80 % de ese máximo, **se apaga solo**, prefiere caerse a dar datos inconsistentes.
- Cuando una transacción lee un valor con un timestamp un poquito mayor que el suyo, pero dentro de ese margen, no sabe si de verdad está en el futuro o si solo es el reloj del otro nodo que va adelantado, a eso le llaman **intervalo de incertidumbre**, y lo resuelve moviendo el timestamp de la transacción después del valor, y si hace falta, **reiniciando la lectura**.
- Por eso piden correr NTP (o chrony) en todos los nodos, el HLC no reemplaza la sincronización, solo hace que los errores pequeños no rompan el orden.

## Comparación

| | PostgreSQL | Kafka | CockroachDB |
|---|---|---|---|
| Qué ordena | `xid` y LSN | offset (y época del líder) | HLC (física + lógica) |
| Tipo | número de secuencia de un solo servidor | número de secuencia del líder de cada partición | híbrido |
| Orden garantizado | total, en un servidor | total, dentro de una partición | serializable, en todo el clúster |
| Si los relojes no coinciden | no le afecta, solo a las fechas | no le afecta, solo al *timestamp* | espera, reinicia lecturas o el nodo se apaga |

Lo que saco de las tres es que cuando hay un solo escritor (el primario de Postgres, el líder de una partición de Kafka), un contador es suficiente y no hace falta ningún reloj, y el problema aparece cuando hay muchos escritores, ahí es donde entran Lamport y el HLC, que es básicamente Lamport con la hora de pared pegada.

## Opcional: un reloj de Lamport en Java

En [`lamport/`](lamport/) está `Lamport.java`, con 3 servidores (`S1`, `S2` y `S3`, cada uno es un hilo con su buzón) que se mandan **4 mensajes**, cada servidor lleva su reloj en un `AtomicLong` y aplica las tres reglas:

- evento local: `reloj.incrementAndGet()` (+1)
- envío: `reloj.incrementAndGet()` y el mensaje lleva ese valor
- recepción: `reloj.updateAndGet(local -> Math.max(local, recibido) + 1)`

Cada evento se imprime con el servidor, qué pasó y su reloj, y al final el programa revisa, para cada mensaje, que el reloj de la recepción sea mayor que el del envío.

**Cómo correrlo** (con Java 17 o más nuevo):

```bash
cd lamport
javac Lamport.java
java Lamport
```

Yo no tengo Java en el PATH, así que lo compilé y lo corrí con la misma imagen de JDK que usa Mexi Banco:

```bash
docker run --rm -v "${PWD}:/lamport" -w /lamport eclipse-temurin:21-jdk-alpine sh -c "javac -d /tmp Lamport.java && java -cp /tmp Lamport"
```

**La salida** (también está en [`lamport/salida.txt`](lamport/salida.txt) y en [`lamport/salida.png`](lamport/salida.png)):

```
srv | evento   | detalle                        | reloj
----+----------+--------------------------------+---------
S1  | LOCAL    | valida la sesion               | reloj = 1
S3  | LOCAL    | limpia cache                   | reloj = 1
S2  | LOCAL    | arranca                        | reloj = 1
S1  | LOCAL    | lee la cuenta                  | reloj = 2
S3  | LOCAL    | rota logs                      | reloj = 2
S1  | LOCAL    | calcula comision               | reloj = 3
S3  | LOCAL    | revisa salud                   | reloj = 3
S3  | LOCAL    | compacta                       | reloj = 4
S1  | ENVIO    | m1 -> S2                       | reloj = 4
S2  | RECIBO   | m1 <- S1 max(1,4)+1            | reloj = 5
S3  | LOCAL    | escribe metricas               | reloj = 5
S2  | ENVIO    | m2 -> S3                       | reloj = 6
S3  | LOCAL    | respalda                       | reloj = 6
S3  | RECIBO   | m2 <- S2 max(6,6)+1            | reloj = 7
S3  | ENVIO    | m3 -> S1                       | reloj = 8
S1  | RECIBO   | m3 <- S3 max(4,8)+1            | reloj = 9
S1  | ENVIO    | m4 -> S2                       | reloj = 10
S2  | RECIBO   | m4 <- S1 max(6,10)+1           | reloj = 11

Revision de causalidad (envio -> recepcion):
  m1: envio S1 con reloj 4 -> recibio S2 con reloj 5  OK
  m2: envio S2 con reloj 6 -> recibio S3 con reloj 7  OK
  m3: envio S3 con reloj 8 -> recibio S1 con reloj 9  OK
  m4: envio S1 con reloj 10 -> recibio S2 con reloj 11  OK
Todos los mensajes se recibieron con un reloj mayor al del envio.
Relojes finales: S1=10, S2=11, S3=8
```

**Por qué la salida respeta la causalidad.** La regla de Lamport dice que si un evento *a* pasó antes que *b* (porque van en el mismo servidor uno después del otro, o porque *a* es el envío de un mensaje y *b* su recepción), entonces el reloj de *a* es menor que el de *b*, y en la salida se cumple siempre:

- En cada servidor el reloj solo sube, S1 va 1, 2, 3, 4, 9, 10, porque cada evento le suma al menos uno.
- Cada recepción queda después de su envío, porque al recibir se toma el **máximo** entre el reloj propio y el del mensaje y se le suma uno, así que nunca puede quedar abajo del que lo mandó. Se ve muy claro en `m1`: S2 iba en 1 y le llegó un mensaje con 4, si se quedara con su propio reloj la recepción tendría 2, un número menor que el del envío, como si la hubiera recibido antes de que se mandara, por eso salta a `max(1,4)+1 = 5`. Lo mismo con `m3`, S1 se había quedado en 4 y le llegó un 8, así que salta a 9.
- La cadena completa `m1 → m2 → m3 → m4` queda con relojes 4 < 5 < 6 < 7 < 8 < 9 < 10 < 11, cada paso de la cadena causal tiene un número más grande que el anterior.

También se ve lo que Lamport **no** dice: `S1 ENVIO m1` y `S3 LOCAL compacta` tienen los dos reloj 4, y `S3 LOCAL escribe metricas` tiene 5 igual que la recepción de `m1` en S2, son eventos **concurrentes**, ninguno pudo influir en el otro, y el reloj no dice cuál fue primero, si se necesitara un orden total habría que desempatar con el nombre del servidor, y si se quisiera saber si dos eventos son concurrentes o no, haría falta un reloj vectorial. El orden de las líneas en pantalla sí puede cambiar de una corrida a otra porque son hilos, pero los valores de los relojes y las revisiones salen igual, porque dependen de los mensajes y no de la hora.

## Fuentes

- PostgreSQL, *Transactions and Identifiers*: https://www.postgresql.org/docs/current/transaction-id.html
- PostgreSQL, *WAL Internals* (LSN): https://www.postgresql.org/docs/current/wal-internals.html
- PostgreSQL, *Date/Time Functions* (`now()` y la hora de inicio de la transacción): https://www.postgresql.org/docs/current/functions-datetime.html
- PostgreSQL, *Replication* (`track_commit_timestamp`): https://www.postgresql.org/docs/current/runtime-config-replication.html
- Apache Kafka, *Documentation* (topics, particiones, offsets, `message.timestamp.type`, productor idempotente): https://kafka.apache.org/documentation/
- KIP-101, *Alter Replication Protocol to use Leader Epoch rather than High Watermark for Truncation*: https://cwiki.apache.org/confluence/display/KAFKA/KIP-101+-+Alter+Replication+Protocol+to+use+Leader+Epoch+rather+than+High+Watermark+for+Truncation
- CockroachDB, *Transaction Layer* (HLC, desfase máximo e intervalo de incertidumbre): https://www.cockroachlabs.com/docs/stable/architecture/transaction-layer
- CockroachDB, *cockroach start* (`--max-offset`, 500 ms por omisión): https://www.cockroachlabs.com/docs/stable/cockroach-start
- Cockroach Labs, *Living Without Atomic Clocks*: https://www.cockroachlabs.com/blog/living-without-atomic-clocks/
- Kulkarni, Demirbas et al., *Logical Physical Clocks and Consistent Snapshots in Globally Distributed Databases* (el paper del HLC, 2014): https://cse.buffalo.edu/tech-reports/2014-04.pdf
- Lamport, *Time, Clocks, and the Ordering of Events in a Distributed System* (1978): https://lamport.azurewebsites.net/pubs/time-clocks.pdf
