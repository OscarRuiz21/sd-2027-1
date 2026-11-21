# T04 · Tiempo y orden en PostgreSQL, MongoDB y RabbitMQ

Derek Santana Cardoso · Sistemas Distribuidos 2027-1 · 3 de noviembre de 2026.
Preparado con asistencia de Codex y consulta de fuentes oficiales. El repaso y
la explicación personal están pendientes; la simulación adjunta fue ejecutada
por el agente en el entorno local, no en una sesión de clase.

## PostgreSQL

**Qué es.** Es una base relacional con transacciones; en Mexi Banco guarda cuentas,
saldos y movimientos.

**Qué usa para ordenar.** El WAL tiene posiciones LSN: desplazamientos en bytes
que aumentan al añadir registros. Son posiciones de secuencia, no horas ni un
reloj vectorial. Ordenan ese flujo de recuperación y replicación. [1]

**Alcance.** Un LSN se interpreta dentro de su historial de WAL; no compara
operaciones de servidores independientes. Tampoco equivale a ordenar las filas
que devuelve un SELECT, ni a decir que todas las transacciones concurrentes se
ejecutaron una detrás de otra. Para la visibilidad importan las instantáneas y
el nivel de aislamiento; para ordenar un resultado se usa ORDER BY. [1, 3]

**Si los relojes discrepan.** La secuencia del WAL no se obtiene ordenando las
horas del sistema. En cambio, un campo de fecha sí puede reflejar un reloj
adelantado. `CURRENT_TIMESTAMP` marca el inicio de la transacción, mientras que
`clock_timestamp()` consulta la hora actual: ni siquiera significan lo mismo.
Sincronizar el sistema no convierte esas fechas en un orden causal global. [2]

Ejemplo conceptual: dos transacciones pueden comenzar en un orden y terminar
en otro; ordenar por su fecha de inicio no reconstruye el orden de confirmación.

## MongoDB

**Qué es.** Almacena documentos BSON y admite replica sets y sharding; permite,
por ejemplo, guardar avisos con campos diferentes según su canal.

**Qué usa para ordenar.** `$clusterTime` es tiempo lógico distribuido entre
servidores y drivers. El driver propaga el mayor valor conocido; `operationTime`
registra el tiempo lógico de la operación de una sesión. Se representan con
BSON Timestamp, distinto de BSON Date: combina segundos y un incremento. [4, 5]
No lo confundo con la fecha de un documento ni con su ObjectId.

**Alcance.** Para la secuencia de operaciones de una sesión causal, lecturas y
escrituras con concern `majority` proporcionan las cuatro garantías: leer lo
propio, lecturas y escrituras monotónicas, y escrituras que siguen a lecturas.
Una lectura puede esperar a alcanzar el tiempo requerido. Esto no significa
que cualquier cliente lea inmediatamente todo, ni aislamiento de otras sesiones.
Las lecturas de secundarios pueden estar atrasadas. [4, 6, 7]

**Si los relojes discrepan.** El tiempo lógico conserva el máximo conocido; no
se decide la causalidad comparando solamente fechas de pared. En la implementación
8.0, el avance considera el reloj físico y valida que un tiempo recibido no esté
demasiado adelantado: un desfase excesivo puede producir error. No es permiso para
mantener relojes arbitrariamente desajustados. Esta descripción del control de
avance se basa en el código de esa versión. [8]

Ejemplo conceptual: guardar un aviso y leerlo después mediante una sesión causal
correctamente configurada expresa una dependencia; ordenar ObjectIds generados
en dos clientes con relojes diferentes no sustituye esa dependencia. [5]

## RabbitMQ (colas AMQP 0-9-1)

**Qué es.** Es un broker: los productores publican y los consumidores reciben
mensajes mediante exchanges y colas.

**Qué usa para ordenar.** La cola conserva una secuencia de ingreso, normalmente
FIFO. No clasifica mensajes por la hora del productor. Los `delivery tag` son
contadores de entregas dentro de un canal, utilizados para confirmar recepción;
no constituyen un reloj global ni un identificador estable de negocio. [9, 10]

**Alcance.** Publicaciones en un canal hacia una cola conservan su orden de
encolado. Con varios canales/productores las secuencias pueden intercalarse.
Prioridades y reentregas alteran el orden observable; varios consumidores pueden
terminar sus tareas en un orden distinto al de entrega. No existe por esto un
orden total compartido por todas las colas. [9]

**Si los relojes discrepan.** El orden de encolado no se recalcula con las fechas
de los productores. Un mensaje con timestamp anterior puede llegar después y
quedar después. Esa independencia no elimina los temporizadores de expiración
ni vuelve confiables las fechas usadas por la aplicación. La aplicación debe
usar versiones o dependencias explícitas si necesita ordenar hechos de negocio.
Esta última es una conclusión del diseño FIFO, no una garantía adicional del broker.

Ejemplo conceptual: recibir A antes de B no implica terminar A antes de B con
consumidores concurrentes. Un ack tampoco demuestra que otro consumidor haya
procesado antes todos los eventos relacionados. [9, 10]

## Comparación

| Tecnología | Mecanismo central aquí | Límite que no debo olvidar |
|---|---|---|
| PostgreSQL | LSN del WAL | No compara historiales independientes |
| MongoDB | Tiempo lógico y sesión causal | Depende de sesión y concerns; no es visibilidad inmediata universal |
| RabbitMQ | Secuencia por cola/canal | Entrega y finalización del procesamiento son cosas distintas |

Ninguna de las tres permite deducir toda la causalidad comparando `fecha` de
máquinas distintas. Primero hay que identificar qué se está ordenando y quién
mantiene ese orden.

## Fuentes oficiales consultadas

1. [PostgreSQL: WAL Internals](https://www.postgresql.org/docs/current/wal-internals.html).
2. [PostgreSQL: Date/Time Functions, Current Date/Time](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT).
3. [PostgreSQL: Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html) y [Sorting Rows](https://www.postgresql.org/docs/current/queries-order.html).
4. [MongoDB Specifications: Causal Consistency](https://specifications.readthedocs.io/en/latest/causal-consistency/causal-consistency/).
5. [MongoDB: BSON Types, Timestamps y ObjectId](https://www.mongodb.com/docs/manual/reference/bson-types/).
6. [MongoDB: Causal Consistency and Read and Write Concerns](https://www.mongodb.com/docs/manual/core/causal-consistency-read-write-concerns/).
7. [MongoDB: Read Isolation, Consistency, and Recency](https://www.mongodb.com/docs/manual/core/read-isolation-consistency-recency/).
8. [MongoDB 8.0: vector_clock.cpp](https://github.com/mongodb/mongo/blob/r8.0.0/src/mongo/db/vector_clock.cpp).
9. [RabbitMQ: Queues, Message Ordering](https://www.rabbitmq.com/docs/queues#message-ordering).
10. [RabbitMQ: Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms).

## Opcional: Lamport en Java

En `lamport/` hay tres servidores simulados en un proceso, cuatro mensajes y
la salida real. No es una prueba de sockets ni de rendimiento. Ejecutar:

```bash
java lamport/Lamport.java
```

En cada evento local/envío se incrementa el reloj; al recibir se toma
`max(local, recibido)+1`. Por ello cada recepción supera a su envío y el reloj
crece dentro de cada servidor. Las comprobaciones ejecutables verifican cada
arista envío→recepción. Por transitividad, también se respeta la cadena causal.
Que L(a)<L(b) no demuestra a→b: los eventos independientes pueden recibir valores
comparables. Para desempatar un orden total artificial se podría usar (reloj, ID),
pero eso tampoco descubre causalidad.
