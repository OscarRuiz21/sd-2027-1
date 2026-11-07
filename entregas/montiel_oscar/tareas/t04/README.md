# T04 · ¿Qué reloj usan las tecnologías que conoces?

Elegí PostgreSQL porque lo usé en Mexi Banco. Para comparar, investigué CockroachDB (una base SQL distribuida que puede correr en la nube) y Amazon Kinesis Data Streams (un servicio administrado para recibir eventos). Los tres necesitan hablar de «antes» y «después», pero no lo resuelven de la misma manera.

## 1. PostgreSQL: posición en un registro

1. **Qué es.** Es una base de datos relacional. En Mexi Banco sirve para guardar cuentas, saldos y movimientos con transacciones.
2. **Qué usa para ordenar.** Para recuperar o replicar cambios, PostgreSQL escribe un registro de escritura anticipada (*WAL*). Cada posición del registro tiene un **LSN** (*Log Sequence Number*), que aumenta conforme se agregan registros. Es una **posición o número de secuencia**, no la hora que marca el reloj. PostgreSQL también ofrece `now()` y `clock_timestamp()`, pero esas horas no son el mecanismo que ordena el WAL: `now()` representa el inicio de la transacción, no necesariamente cuándo terminó.
3. **Hasta dónde llega el orden.** Los registros tienen un orden dentro del WAL de **una instancia primaria** y una réplica puede avanzar siguiendo esas posiciones. Eso no significa que cualquier `SELECT` devuelva filas en orden: para eso hay que escribir `ORDER BY`. Tampoco hay un único LSN comparable entre dos bases primarias independientes.
4. **Si los relojes no coinciden.** El WAL sigue teniendo posiciones ordenadas: no necesita igualar relojes de pared para reproducirlo. En cambio, si mi aplicación ordena movimientos por una columna de hora tomada de distintos servidores, una hora adelantada puede mostrar un orden engañoso. Para el historial visible elegiría una clave de orden explícita y definiría cómo desempatar, no confiaría solo en timestamps.

**Ejemplo:** dos movimientos pueden traer horas muy cercanas o incluso iguales; sus posiciones en el WAL son distintas, aunque el usuario no debería consultar el WAL como si fuera una columna de su tabla.

## 2. CockroachDB: reloj híbrido en varios nodos

1. **Qué es.** Es una base SQL distribuida que reparte y replica datos entre nodos. Es relevante si una aplicación necesita una base transaccional que crezca en varias máquinas o regiones.
2. **Qué usa para ordenar.** Usa un **reloj lógico híbrido (HLC)**: combina una parte física, cercana al tiempo real, con una parte lógica que permite avanzar cuando dos eventos ocurren demasiado cerca o llegan mensajes de otro nodo. Asigna timestamps a las transacciones y usa versiones de datos (*MVCC*).
3. **Hasta dónde llega el orden.** No se limita a un proceso ni a un shard: las transacciones confirmadas de la base tienen aislamiento **serializable**, es decir, su resultado debe poder explicarse como si hubieran ocurrido en algún orden secuencial, incluso si tocaron varios nodos. Eso no convierte al HLC en una lista sencilla de «quién llegó primero» para cada par de transacciones simultáneas; y una consulta SQL sigue necesitando `ORDER BY` si quiero filas ordenadas.
4. **Si los relojes no coinciden.** CockroachDB necesita que la diferencia entre relojes permanezca dentro de un límite configurado; recomienda sincronizarlos (por ejemplo, con NTP). Cuando una lectura encuentra una versión cuya fecha cae en una zona de incertidumbre, puede **reintentar la transacción** con un timestamp mayor. Si el desfase es excesivo, el nodo puede detenerse para no comprometer la consistencia. El límite predeterminado documentado es de 500 ms.

**Ejemplo:** un nodo cree que son las 10:00:00.100 y otro que son las 10:00:00.300. Una lectura no debería asumir sin más que un dato «del futuro» aún no existía; puede tener que reintentarse.

## 3. Amazon Kinesis Data Streams: secuencia por shard

1. **Qué es.** Es un servicio de AWS para recibir y procesar flujos de eventos. Por ejemplo, Mexi Banco podría enviar eventos de transferencias para producir métricas o notificaciones. No sustituye por sí solo a la base que guarda los saldos.
2. **Qué usa para ordenar.** Kinesis asigna un **número de secuencia** a cada registro después de recibirlo. Con distribución por llave de partición (la modalidad que considero aquí), la llave determina el shard. Para eventos de una misma cuenta, podría usar la CLABE como llave.
3. **Hasta dónde llega el orden.** Al leer un shard, los registros salen en orden de número de secuencia. Los de la misma llave van al mismo shard mientras esa distribución aplica; **no hay un orden global útil entre shards**. Si un productor necesita garantizar que sus envíos sucesivos para una llave tengan números estrictamente crecientes, debe encadenar llamadas `PutRecord` usando `SequenceNumberForOrdering`: envíos casi simultáneos no bastan para garantizarlo. Al cambiar el número de shards, la aplicación consumidora debe respetar la relación entre shards padre e hijo para conservar el orden por llave. La modalidad nueva de distribución administrada por el servicio está pensada para cargas donde ese orden no es requisito; aquí no la estoy usando.
4. **Si los relojes no coinciden.** Para leer en orden dentro del shard no se comparan las horas de las máquinas productoras: manda el número que asignó Kinesis. Un timestamp incluido en el JSON puede servir para mostrar cuándo *cree* el productor que ocurrió algo, pero no para reconstruir por sí solo el orden de aceptación entre productores.

**Ejemplo:** los eventos de la cuenta A pueden ser A1, A2 y A3 en su shard; los de la cuenta B pueden avanzar a la vez en otro shard. No puedo afirmar si A2 ocurrió antes que B2 mirando únicamente esos números.

## Lo que me llevo

«Orden» no significa lo mismo en todas partes: PostgreSQL puede ordenar su **registro**, CockroachDB coordina **transacciones entre nodos** y Kinesis ordena **eventos dentro de un shard**. Ninguno convierte automáticamente las horas de pared de varias máquinas en una verdad absoluta. Antes de elegir una tecnología preguntaría *qué* necesito ordenar (cambios, transacciones o mensajes) y *en qué alcance* (una base, una cuenta o todo el sistema).

## Fuentes

- PostgreSQL, [WAL Internals](https://www.postgresql.org/docs/current/wal-internals.html), [funciones de fecha y hora](https://www.postgresql.org/docs/current/functions-datetime.html) y [`ORDER BY`](https://www.postgresql.org/docs/current/queries-order.html).
- Cockroach Labs, [Hybrid Logical Clock (HLC) Timestamps](https://www.cockroachlabs.com/glossary/distributed-db/hybrid-logical-clock-hlc-timestamps/), [Developer Basics](https://www.cockroachlabs.com/docs/stable/developer-basics.html) y [Clock Management in CockroachDB](https://www.cockroachlabs.com/blog/clock-management-cockroachdb/).
- AWS, [Kinesis Data Streams: conceptos](https://docs.aws.amazon.com/streams/latest/dev/key-concepts.html), [envío y orden de registros](https://docs.aws.amazon.com/streams/latest/dev/developing-producers-with-sdk.html), [orden al cambiar shards](https://docs.aws.amazon.com/streams/latest/dev/kinesis-record-processor-scaling.html) y [distribución administrada](https://docs.aws.amazon.com/streams/latest/dev/service-managed-record-distribution.html).
