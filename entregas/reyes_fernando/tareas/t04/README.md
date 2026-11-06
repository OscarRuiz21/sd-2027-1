# T04 - ¿Qué reloj usan las tecnologías que conoces?

**Alumno:** Reyes Vázquez Fernando

## Introducción

En un sistema distribuido no podemos confiar solamente en la hora de cada computadora, porque sus relojes pueden estar adelantados o atrasados. Por eso, las tecnologías utilizan diferentes mecanismos para decidir qué ocurrió primero. Para esta tarea elegí Kafka, PostgreSQL y Google Cloud Spanner, porque muestran distintas formas de establecer el orden sin depender solamente de la hora de pared.

## 1. Apache Kafka

**1. ¿Qué es y para qué se usa?**

Kafka es una plataforma que permite enviar, almacenar y leer eventos entre aplicaciones. Por ejemplo, una tienda en línea puede publicar mensajes sobre pedidos y otros servicios pueden leerlos para actualizar el inventario o enviar notificaciones. Los mensajes se organizan por temas (*topics*) y cada tema puede dividirse en particiones.

**2. ¿Qué tiempo usa para ordenar?**

Para establecer el orden de los mensajes guardados, Kafka usa un **número de secuencia llamado `offset`**, que indica la posición de cada mensaje dentro de su partición. No necesita comparar la hora de pared de los servidores para establecer ese orden. Un mensaje puede tener una fecha u hora asociada, pero el orden del registro se basa en el `offset`.

**3. ¿Qué orden garantiza y hasta dónde?**

Kafka mantiene un orden dentro de **cada partición**, no uno solo para todas las particiones. Por ejemplo, si tres eventos de un pedido quedan en la misma partición, se pueden leer en el orden en que Kafka los guardó. Pero si los eventos están repartidos entre dos particiones, sus `offsets` no permiten saber cuál ocurrió primero en todo el sistema. Además, ordenar el registro no significa que dos productores diferentes hayan generado sus eventos en ese mismo orden en la vida real.

**4. ¿Qué pasa cuando los relojes no coinciden?**

Kafka no necesita que los relojes de las máquinas coincidan para ordenar los mensajes de una partición: se guía por el lugar donde quedaron guardados. Aunque una computadora tenga la hora adelantada, eso no cambia el `offset` asignado. Por ello, sincronizar con NTP puede ser útil para otras funciones, pero no es lo que establece el orden de los mensajes.

## 2. PostgreSQL

**1. ¿Qué es y para qué se usa?**

PostgreSQL es un sistema de bases de datos relacional. Se utiliza para guardar y consultar información, por ejemplo usuarios, productos, pagos o movimientos bancarios. También permite hacer transacciones, es decir, agrupar operaciones para que se confirmen juntas o se cancelen si ocurre un problema.

**2. ¿Qué tiempo usa para ordenar?**

Para registrar los cambios, PostgreSQL utiliza el **WAL (*Write-Ahead Log*)**, que es como una bitácora donde escribe información antes de dar por guardadas las modificaciones. Cada posición en ese registro se identifica con un **LSN (*Log Sequence Number*)**, que va avanzando conforme se escriben nuevos registros.

Por eso, en este caso elegí un **número de secuencia o posición**, no un reloj físico ni un reloj de Lamport. Un LSN no representa una hora; indica dónde se encuentra cierta información en el WAL. PostgreSQL también usa identificadores de transacción y el mecanismo MVCC para controlar qué cambios puede ver cada consulta, pero esos identificadores no deben confundirse con el orden exacto en que todas las transacciones terminan.

**3. ¿Qué orden garantiza y hasta dónde?**

El WAL conserva un orden de sus **registros de cambios dentro de un mismo clúster de PostgreSQL y una misma línea de historial**. Esto permite recuperar la base de datos y reproducir esos cambios en una réplica. Por ejemplo, si un servidor principal registra varias modificaciones, una réplica puede seguir el WAL para aplicarlas en el orden correspondiente.

Sin embargo, eso **no significa que exista un orden global entre varias bases de PostgreSQL independientes**, ni que un `SELECT` devuelva los resultados en el orden del WAL. Si queremos mostrar movimientos ordenados por fecha o por identificador, debemos usar `ORDER BY` en la consulta. Además, una réplica asíncrona puede tardar en mostrar cambios que ya se confirmaron en el servidor principal.

**4. ¿Qué pasa cuando los relojes no coinciden?**

El orden de los registros del WAL **no depende de comparar la hora de pared** de las computadoras. PostgreSQL se guía por las posiciones LSN, así que un reloj adelantado o atrasado no cambia la posición de un registro en el WAL. Sin embargo, sí conviene sincronizar los relojes con herramientas como NTP para que las fechas guardadas y los registros de diagnóstico tengan sentido. Si se ordenan eventos solamente por una columna de fecha creada en distintos servidores, las diferencias entre sus relojes pueden provocar confusiones.

## 3. Google Cloud Spanner (TrueTime)

**1. ¿Qué es y para qué se usa?**

Spanner es una base de datos distribuida de Google Cloud que puede guardar y consultar información desde diferentes servidores e incluso diferentes regiones. Es útil para aplicaciones que necesitan trabajar con datos compartidos sin perder la consistencia, como operaciones bancarias o sistemas de compras.

**2. ¿Qué tiempo usa para ordenar?**

Spanner utiliza **TrueTime**, un sistema basado en tiempo físico que toma en cuenta que los relojes no son perfectos. En lugar de considerar que la hora actual es un número totalmente exacto, TrueTime trabaja con un intervalo que representa la incertidumbre del reloj. Con esa información, Spanner asigna marcas de tiempo a sus transacciones.

**3. ¿Qué orden garantiza y hasta dónde?**

En las transacciones con aislamiento serializable, Spanner ofrece **consistencia externa** en toda la base de datos, incluso cuando intervienen varias regiones. Esto significa que las operaciones se comportan como si se hubieran ejecutado una tras otra y se respeta el orden observable: si una transacción termina antes de que otra empiece a confirmarse, la segunda debe quedar después en el orden de confirmación. No se limita a una sola partición.

**4. ¿Qué pasa cuando los relojes no coinciden?**

Spanner no supone que todos marcan exactamente lo mismo. TrueTime expresa un margen de error y Spanner puede **esperar antes de terminar de confirmar una transacción** hasta estar seguro de que su marca de tiempo ya quedó en el pasado. A esta medida se le conoce como *commit wait*. En vez de ignorar la diferencia entre relojes, la considera para mantener un orden confiable.

## Comparación final

| Tecnología | ¿Con qué establece el orden? | ¿Hasta dónde llega el orden? | Si los relojes difieren... |
|---|---|---|---|
| Kafka | `offset` asignado a cada mensaje | Dentro de cada partición | El orden del registro no depende de la hora de pared |
| PostgreSQL | Posición LSN en el registro WAL | Registros WAL de un mismo clúster e historial | El orden del WAL se mantiene por posiciones, aunque las horas difieran |
| Spanner | TrueTime, con incertidumbre del reloj físico | Transacciones serializables en toda la base de datos | Considera el error del reloj y puede esperar al confirmar |

Lo que entendí es que **no hay una sola forma de ordenar eventos distribuidos**. Kafka organiza los mensajes mediante posiciones dentro de sus particiones; PostgreSQL registra cambios en un WAL con posiciones crecientes; y Spanner usa el tiempo físico, pero reconociendo que tiene un margen de error. Los tres ejemplos muestran por qué la hora de pared, por sí sola, no basta para decidir qué pasó primero en todo un sistema.

## Ejercicio opcional: reloj de Lamport en Java

Para la parte opcional hice una simulación de **tres servidores (A, B y C)** que se mandan **cuatro mensajes (M1, M2, M3 y M4)**. Cada servidor tiene su propio reloj lógico, guardado en un `AtomicLong`. No son servidores reales en una red: son objetos de Java que permiten ver, paso por paso, cómo se actualiza cada reloj.

### Reglas que usa el programa

- **Evento local:** el servidor aumenta su reloj en 1.
- **Envío:** aumenta su reloj en 1 y agrega esa marca al mensaje.
- **Recepción:** toma el mayor valor entre su reloj y la marca recibida, y después suma 1: `max(local, recibido) + 1`.

### Cómo se hizo la simulación

Primero, A prepara un pedido y envía **M1 a B** y **M2 a C**. El mensaje M1 queda pendiente por un momento. C recibe M2, procesa un cambio y envía **M3 a B**. Después, B recibe M3 **antes que M1**, como ejemplo de un mensaje que se retrasó. Finalmente, B envía **M4 a A**.

Esto permite notar que los mensajes no siempre llegan en el mismo orden en que se enviaron, pero los relojes lógicos siguen avanzando.

### Por qué la salida respeta la causalidad

En cada recepción se aplica `max(local, recibido) + 1`, así que **el reloj de quien recibe siempre es mayor que la marca del mensaje enviado**:

| Mensaje | Envío | Recepción |
|---|---|---|
| M1: A a B | A, reloj 2 | B, reloj 8 |
| M2: A a C | A, reloj 3 | C, reloj 4 |
| M3: C a B | C, reloj 6 | B, reloj 7 |
| M4: B a A | B, reloj 10 | A, reloj 11 |

Por ejemplo, cuando B recibe M3, su reloj pasa a **7**. Más tarde recibe M1, que trae una marca anterior (**2**), pero B no vuelve atrás: su reloj pasa a **8**. Con esto se respeta la regla de Lamport: **si un evento es causa de otro, el primero tiene una marca menor**. Sin embargo, una marca menor no demuestra por sí sola que dos eventos estén relacionados, y Lamport **no obliga a que los mensajes se entreguen en orden causal**.

### Compilar y ejecutar

Desde la carpeta `t04/` se pueden usar estos comandos:

```bash
cd lamport
javac LamportSimulacion.java
java LamportSimulacion
```

El archivo `salida.txt` contiene la salida obtenida al ejecutar el programa. Para volver a generarlo:

```bash
java LamportSimulacion > salida.txt
```

No es necesario entregar los archivos `.class` que genera Java.

## Fuentes consultadas

- Apache Software Foundation. (s. f.). *Introduction to Apache Kafka*. https://kafka.apache.org/documentation/
- PostgreSQL Global Development Group. (s. f.). *WAL Internals*. https://www.postgresql.org/docs/current/wal-internals.html
- PostgreSQL Global Development Group. (s. f.). *Log-Shipping Standby Servers*. https://www.postgresql.org/docs/current/warm-standby.html
- PostgreSQL Global Development Group. (s. f.). *Sorting Rows (ORDER BY)*. https://www.postgresql.org/docs/current/queries-order.html
- PostgreSQL Global Development Group. (s. f.). *Transactions and Identifiers*. https://www.postgresql.org/docs/current/transaction-id.html
- Google Cloud. (s. f.). *Spanner: TrueTime and external consistency*. https://docs.cloud.google.com/spanner/docs/true-time-external-consistency
- Google Cloud. (s. f.). *Life of Spanner reads and writes*. https://docs.cloud.google.com/spanner/docs/whitepapers/life-of-reads-and-writes
- Lamport, L. (1978). *Time, clocks, and the ordering of events in a distributed system*. Communications of the ACM, 21(7), 558-565. https://doi.org/10.1145/359545.359563
