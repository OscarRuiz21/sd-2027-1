# T04 · ¿Qué reloj usan las tecnologías que conozco?

## Introducción

En sistemas distribuidos no siempre se puede confiar en la hora que marca cada computadora para saber qué operación ocurrió primero. Dos servidores pueden tener algunos milisegundos de diferencia, aunque los dos estén sincronizados con NTP. Por eso, varias tecnologías utilizan identificadores, secuencias o relojes lógicos para establecer el orden de sus operaciones.

Para esta actividad elegí PostgreSQL, MongoDB y Apache Kafka. Las tres trabajan con datos, pero resuelven el problema de ordenar operaciones de manera diferente. Algo que me parece importante es distinguir entre la fecha que se guarda para saber aproximadamente cuándo ocurrió un evento y el mecanismo que la tecnología utiliza realmente para decidir su orden.

## 1. PostgreSQL

### 1. ¿Qué es y para qué se usa?

PostgreSQL es un sistema gestor de bases de datos relacional y de código abierto. Se utiliza para guardar información organizada en tablas, relacionarla y consultarla mediante SQL. Es común encontrarlo en aplicaciones que necesitan transacciones y mantener la integridad de sus datos, por ejemplo, sistemas de ventas, bancos o plataformas administrativas (PostgreSQL Global Development Group, 2025).

### 2. ¿Qué tiempo usa para ordenar?

PostgreSQL no depende de un reloj de pared para decidir el orden interno de las transacciones. Utiliza identificadores y posiciones dentro de su registro de escritura:

- **XID (Transaction ID):** es un identificador numérico que se asigna secuencialmente cuando una transacción realiza su primera escritura. PostgreSQL lo utiliza como parte de su mecanismo MVCC, que permite manejar distintas versiones de los registros y determinar cuáles son visibles para cada transacción. Es importante aclarar que un XID menor no significa necesariamente que esa transacción haya comenzado antes o se haya confirmado antes que otra; solamente indica el orden en que se asignaron esos identificadores (PostgreSQL Global Development Group, 2022).
- **LSN (Log Sequence Number):** identifica una posición dentro del registro WAL (Write-Ahead Log). Es un número que aumenta conforme se agregan registros al WAL. Este registro conserva los cambios de la base de datos y permite recuperarlos o reproducirlos en una réplica (PostgreSQL Global Development Group, 2026; 2025).

Por lo tanto, para el orden interno de los cambios, PostgreSQL utiliza principalmente números de secuencia generados por el propio motor, no un reloj lógico vectorial ni la comparación de horas de diferentes servidores.

También tiene funciones de fecha y hora. Por ejemplo, NOW() y CURRENT_TIMESTAMP devuelven la hora de inicio de la transacción, mientras que clock_timestamp() devuelve la hora actual en el momento de llamarla. Esas fechas sirven para registrar tiempos, pero no son lo que determina el orden de las transacciones (PostgreSQL Global Development Group, 2026).

### 3. ¿Qué orden garantiza y hasta dónde?

Dentro de una misma instancia o clúster de PostgreSQL, el WAL mantiene una secuencia ordenada de registros. Esto permite que el servidor principal y sus réplicas reproduzcan los cambios en el orden registrado. Por otro lado, MVCC utiliza los XID y el estado de las transacciones para decidir qué versiones de los datos puede consultar cada operación.

La garantía no debe confundirse con un orden cronológico universal: PostgreSQL no puede afirmar que dos transacciones independientes de diferentes instalaciones ocurrieron en determinado orden solamente comparando sus XID o sus fechas. Tampoco se debe interpretar el XID como el orden exacto de confirmación de las transacciones.

### 4. ¿Qué pasa cuando los relojes no coinciden?

Si el reloj del sistema operativo está adelantado o atrasado, las fechas guardadas o devueltas por las funciones de PostgreSQL pueden ser incorrectas respecto a la hora real. Sin embargo, eso no cambia por sí solo la secuencia de los registros WAL ni el funcionamiento de los identificadores de transacción.

PostgreSQL no utiliza NTP como mecanismo para ordenar sus transacciones. La sincronización del reloj físico se administra a nivel del sistema operativo; dentro de la base de datos, el orden de los cambios se apoya en sus identificadores y en el WAL.

## 2. MongoDB

### 1. ¿Qué es y para qué se usa?

MongoDB es un sistema gestor de bases de datos orientado a documentos. En lugar de organizar toda la información en tablas y filas, almacena documentos BSON, que tienen una estructura parecida a JSON. Se utiliza cuando se necesita manejar información flexible, por ejemplo, perfiles, catálogos, registros de actividad o datos de aplicaciones web.

### 2. ¿Qué tiempo usa para ordenar?

En una implementación distribuida, como un conjunto de réplicas o un clúster fragmentado, MongoDB utiliza un reloj lógico conocido como clusterTime. También maneja `operationTime`, que representa el tiempo lógico asociado a una operación. La propia documentación indica que estos valores se utilizan para ordenar operaciones en conjuntos de réplicas y clústeres fragmentados (MongoDB, 2022).

Estos valores se relacionan con el tipo interno BSON Timestamp. Este tipo tiene dos partes: una cantidad de segundos desde la época Unix y un contador que aumenta para distinguir operaciones ocurridas durante el mismo segundo. En el registro de operaciones (oplog), el campo ts utiliza este tipo de marca de tiempo. No debe confundirse con el tipo BSON Date, que representa una fecha normal en milisegundos desde la época Unix y se utiliza comúnmente para fechas de la aplicación (MongoDB, 2025).

En otras palabras, MongoDB no se limita a comparar la fecha que guardó el usuario en un documento: en sus despliegues distribuidos maneja un tiempo lógico para coordinar el orden de las operaciones. Es un valor lógico escalar, no un reloj vectorial que mantenga un contador independiente por cada nodo. Esta función de clusterTime aplica a conjuntos de réplicas y clústeres fragmentados; no hay que asumir que está disponible de la misma manera en una instalación independiente (standalone).

### 3. ¿Qué orden garantiza y hasta dónde?

MongoDB puede mantener el orden causal de operaciones relacionadas. Por ejemplo, si una aplicación realiza una escritura y después una lectura que depende de ese cambio, una sesión con consistencia causal permite que la lectura respete esa dependencia.

Para obtener las cuatro garantías de consistencia causal —leer las propias escrituras, lecturas monotónicas, escrituras monotónicas y que las escrituras sigan a las lecturas— se necesita utilizar sesiones causalmente consistentes con `readConcern: majority en las lecturas y writeConcern: majority en las escrituras. Estas garantías permiten mantener la relación causal entre miembros del conjunto de réplicas, pero no significan que todas las operaciones independientes del sistema tengan un orden cronológico global como si existiera un único reloj perfecto (MongoDB, 2026).

### 4. ¿Qué pasa cuando los relojes no coinciden?

Para mantener el orden causal, MongoDB utiliza los tiempos lógicos que se comparten entre los miembros, en lugar de depender únicamente de comparar las fechas físicas de cada equipo. Un ejemplo es afterClusterTime: cuando una lectura necesita observar datos al menos hasta cierto tiempo lógico, el nodo debe esperar a que su oplog alcance ese tiempo antes de atenderla. Si todavía no ha llegado, la lectura espera (MongoDB, 2023).

La sincronización de los relojes físicos sigue siendo útil para que las fechas, registros y métricas sean coherentes, pero la relación causal se maneja mediante el tiempo lógico y los mecanismos de replicación de MongoDB.

## 3. Apache Kafka

### 1. ¿Qué es y para qué se usa?

Apache Kafka es una plataforma distribuida de transmisión de eventos. Se utiliza para publicar, almacenar, leer y procesar flujos de información entre diferentes aplicaciones o servicios. Por ejemplo, puede recibir eventos de compras, pagos o actividad de usuarios y permitir que distintos sistemas los consuman sin tener que comunicarse directamente entre sí (Apache Software Foundation, 2026).

### 2. ¿Qué tiempo usa para ordenar?

Kafka utiliza principalmente un número de secuencia llamado offset. Los mensajes se almacenan en tópicos, y cada tópico se divide en particiones. Dentro de cada partición, los mensajes se agregan a un registro ordenado y reciben una posición que permite identificarlos y leerlos en secuencia (Apache Software Foundation, 2026).

El offset no representa una hora: es una posición dentro de la partición. Kafka también guarda una marca de tiempo en cada evento. De forma predeterminada, puede utilizar CreateTime, que corresponde al momento indicado por el productor. También se puede configurar LogAppendTime, para que el broker asigne la hora en la que recibe y agrega el mensaje a su registro (Apache Software Foundation, 2026).

Por eso, la marca de tiempo del evento y el offset cumplen funciones distintas: la primera describe una fecha; el segundo determina su posición en la secuencia.

### 3. ¿Qué orden garantiza y hasta dónde?

Kafka garantiza que los consumidores lean los mensajes de una partición en el mismo orden en que fueron escritos. Cuando los mensajes tienen la misma llave y se utiliza la partición basada en esa llave, se envían a la misma partición; así se puede conservar el orden de los eventos de un cliente, una cuenta o un pedido específico.

La garantía llega hasta la partición, no hasta todo el tópico. Si un tópico tiene varias particiones, Kafka no establece un único orden total entre los mensajes de todas ellas. Por ejemplo, el mensaje con offset 15 de una partición no se puede comparar directamente con el mensaje con offset 15 de otra partición para saber cuál ocurrió primero (Apache Software Foundation, 2026).

### 4. ¿Qué pasa cuando los relojes no coinciden?

Si Kafka utiliza CreateTime, los productores pueden tener sus relojes desajustados y los eventos podrían mostrar fechas que parecen estar fuera de orden. Aun así, Kafka conserva el orden de lectura definido por los offsets de cada partición.

Si se configura LogAppendTime, el broker utiliza su propio reloj físico para asignar la marca de tiempo al recibir el evento. Esto cambia la referencia de la fecha, pero no el mecanismo de ordenamiento: los consumidores siguen utilizando los offsets. Kafka también tiene parámetros para limitar la diferencia permitida entre la marca de tiempo del mensaje y la del broker cuando se utiliza CreateTime; eso sirve para validar fechas, no para reordenar los mensajes (Apache Software Foundation, 2026).

## Comparación final

| Tecnología | Mecanismo que establece el orden | Alcance principal |
|---|---|---|
| PostgreSQL | XID para MVCC y LSN para la secuencia del WAL. | Orden de registros dentro de una instancia o clúster; las réplicas reproducen el WAL. |
| MongoDB | Reloj lógico clusterTime y marcas Timestamp. | Relaciones causales entre operaciones, con las opciones de consistencia adecuadas. |
| Apache Kafka | offset asignado dentro de cada partición. | Orden de los mensajes de una partición; no hay orden total entre todas las particiones. |

## Conclusión

Después de revisar estas tecnologías, me queda más claro que no existe una sola forma de ordenar los eventos en un sistema distribuido. PostgreSQL utiliza identificadores y posiciones dentro de su registro de escritura para controlar las transacciones y la recuperación de los datos. MongoDB utiliza un reloj lógico para conservar las relaciones causales entre operaciones que se realizan en distintos miembros. En cambio, Kafka maneja números de secuencia que permiten leer los mensajes en el orden en que fueron agregados a cada partición.

Lo que tienen en común es que no necesitan confiar únicamente en la hora de pared para establecer el orden que necesitan. La hora física sigue siendo útil para registrar cuándo ocurrió algo, pero puede tener diferencias entre equipos. Por eso considero importante distinguir entre una fecha y un mecanismo de ordenamiento: una fecha puede decir cuándo parece haber ocurrido un evento, mientras que un identificador, un reloj lógico o un offset puede indicar qué operación debe considerarse anterior dentro del alcance que garantiza cada tecnología.

## Referencias

Apache Software Foundation. (2026). *Broker configs (Apache Kafka 4.4 documentation).* Recuperado el 6 de noviembre de 2026, de https://kafka.apache.org/44/configuration/broker-configs/

Apache Software Foundation. (2026). *Introduction (Apache Kafka 4.4 documentation).* Recuperado el 6 de noviembre de 2026, de https://kafka.apache.org/44/getting-started/introduction/

Apache Software Foundation. (2026). *Protocol (Apache Kafka 4.4 documentation).* Recuperado el 6 de noviembre de 2026, de https://kafka.apache.org/44/design/protocol/

MongoDB. (2025). *BSON types.* Recuperado el 6 de noviembre de 2026, de https://www.mongodb.com/docs/manual/reference/bson-types/

MongoDB. (2026). *Causal consistency and read and write concerns.* Recuperado el 6 de noviembre de 2026, de https://www.mongodb.com/docs/manual/core/causal-consistency-read-write-concerns/

MongoDB. (2023). *Read concern.* Recuperado el 6 de noviembre de 2026, de https://www.mongodb.com/docs/manual/reference/read-concern/

MongoDB. (2022). *Use database commands.* Recuperado el 6 de noviembre de 2026, de https://www.mongodb.com/docs/manual/tutorial/use-database-commands/

PostgreSQL Global Development Group. (2026). *About PostgreSQL.* Recuperado el 6 de noviembre de 2026, de https://www.postgresql.org/about/

PostgreSQL Global Development Group. (2025). *Date/time functions and operators (PostgreSQL 17 documentation).* Recuperado el 6 de noviembre de 2026, de https://www.postgresql.org/docs/17/functions-datetime.html

PostgreSQL Global Development Group. (2026). *Glossary (PostgreSQL 17 documentation).* Recuperado el 6 de noviembre de 2026, de https://www.postgresql.org/docs/17/glossary.html

PostgreSQL Global Development Group. (2026). *System administration functions (PostgreSQL 17 documentation).* Recuperado el 6 de noviembre de 2026, de https://www.postgresql.org/docs/17/functions-admin.html

PostgreSQL Global Development Group. (2026). *Transactions and identifiers (PostgreSQL 17 documentation).* Recuperado el 6 de noviembre de 2026, de https://www.postgresql.org/docs/17/transaction-id.html
