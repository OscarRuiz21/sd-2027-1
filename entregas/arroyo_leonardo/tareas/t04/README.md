# T04: ¿Qué reloj usan las tecnologías que conoces?

En un sistema distribuido, varias computadoras pueden recibir mensajes o modificar información al mismo tiempo. Para saber qué ocurrió primero, podría parecer suficiente revisar la hora de cada máquina. El problema es que sus relojes pueden estar adelantados o atrasados. Por ello, una hora mayor no siempre significa que un evento ocurrió después.

Un **reloj físico o de pared** representa la fecha y la hora. Un **reloj lógico**, como el de Lamport, utiliza contadores y el intercambio de mensajes para respetar relaciones de causalidad. Aquí, causalidad significa que un evento influye en otro: por ejemplo, primero se envía un mensaje y después se recibe. También existen sistemas que asignan números de secuencia para establecer un orden sin depender de la hora.

Para esta tarea elegí **Apache Kafka, Apache Cassandra y Google Spanner**. Cada una resuelve un problema diferente y permite ver por qué el mecanismo utilizado para ordenar también cambia.

## Apache Kafka

### 1. Qué es y para qué se usa

Apache Kafka es una plataforma distribuida para almacenar y transmitir eventos. Un evento puede representar un pedido creado, un pago recibido o una lectura de un sensor. Las aplicaciones que envían eventos se llaman **productores**, y las que los leen se llaman **consumidores**. Los eventos se organizan en temas o *topics*, que se dividen en **particiones** para repartir el trabajo entre servidores. [Introducción de Kafka](https://kafka.apache.org/intro/).

### 2. Qué tiempo usa para ordenar

Kafka utiliza principalmente un **número de secuencia llamado offset** para identificar la posición de cada registro dentro de una partición. El servidor de Kafka, o **broker**, que actúa como líder de esa partición asigna los offsets al agregar los registros. Por ello, el orden del registro depende de su posición en la partición, no de la hora escrita en el mensaje. [Diseño de Kafka](https://kafka.apache.org/42/design/design/).

Cada mensaje también puede llevar un **timestamp**, que es una marca de tiempo. Con `CreateTime`, la marca viene del productor; con `LogAppendTime`, corresponde al momento en que el broker agrega el mensaje. Estas marcas tienen otros usos, pero no sustituyen al offset como orden de la partición. [Configuración de topics](https://kafka.apache.org/42/configuration/topic-configs/).

### 3. Qué orden garantiza y hasta dónde

Kafka conserva el orden de los registros **dentro de cada partición**. Si una partición contiene los offsets 20 y 21, un consumidor que avanza por ella encuentra primero el 20. Sin embargo, el offset 20 de una partición no se puede comparar con el 20 de otra para saber cuál ocurrió antes. No existe un orden total entre todas las particiones de un topic. [Diseño de Kafka](https://kafka.apache.org/42/design/design/).

Si los eventos de un pedido se envían siempre a la misma partición usando su identificador como clave, pueden conservar ese orden juntos. Esto requiere mantener una asignación consistente de la clave a la partición. [Introducción de Kafka](https://kafka.apache.org/intro/).

También hay que distinguir el orden guardado del orden en que la aplicación intentó enviar los mensajes. La **idempotencia del productor** evita duplicados por reintentos. Si se desactiva y hay varias solicitudes pendientes de respuesta, los reintentos pueden alterar el orden de llegada. La configuración del productor debe preservar el orden cuando esto sea necesario. [Configuración del productor](https://kafka.apache.org/42/configuration/producer-configs/).

### 4. Qué pasa cuando los relojes no coinciden

Un productor adelantado puede enviar una marca de tiempo mayor que la de un mensaje que llega después. Kafka no reorganiza la partición por esa diferencia: mantiene el orden de offsets. Según la configuración, puede rechazar marcas de `CreateTime` demasiado alejadas de la hora del broker. Con `LogAppendTime` utiliza la hora del broker, pero eso tampoco crea un orden global entre particiones. [Configuración de topics](https://kafka.apache.org/42/configuration/topic-configs/).

## Apache Cassandra

### 1. Qué es y para qué se usa

Apache Cassandra es una base de datos NoSQL distribuida. Reparte datos y mantiene réplicas para atender muchas operaciones con disponibilidad. Puede almacenar registros de actividad. Aquí reviso sus escrituras normales. [Arquitectura de Cassandra](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html).

### 2. Qué tiempo usa para ordenar

Cassandra utiliza marcas de tiempo para resolver conflictos mediante **Last Write Wins**, o “la última escritura gana”. Cuando existen versiones diferentes de una misma celda, se conserva la que tenga el timestamp más alto. El tiempo puede venir del cliente o, si no se proporciona, del nodo coordinador que recibe la operación. [Arquitectura de Cassandra](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html).

Las marcas de escritura se expresan en **microsegundos**. Su base habitual es el reloj físico, aunque algunos clientes agregan mecanismos locales para que las marcas siempre aumenten. Por ejemplo, el driver de Java incluye un generador que puede incrementar artificialmente el timestamp si el reloj retrocede. Esa propiedad pertenece a ese generador; no significa que todos los clientes compartan un reloj lógico de Lamport. [Query Timestamps del driver de Java](https://apache.github.io/cassandra-java-driver/4.18.0/core/query_timestamps/).

### 3. Qué orden garantiza y hasta dónde

La comparación se realiza **por celda**: el valor de una columna en una fila. No establece un orden global ni garantiza que el mayor timestamp corresponda a la escritura más reciente en tiempo real. [Arquitectura de Cassandra](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html).

Si dos operaciones tienen el mismo timestamp, existen reglas de desempate. Una eliminación tiene prioridad sobre una escritura; entre valores vivos con la misma marca, se utiliza la comparación de sus valores para decidir de forma determinista. Así, las réplicas pueden llegar a la misma decisión sin saber cuál operación ocurrió primero. [Timestamps en Cassandra](https://docs.datastax.com/en/dse/6.8/drivers/timestamps.html).

Además, poner operaciones en un `BATCH` no obliga a resolverlas en el orden escrito: si comparten timestamp, pueden intervenir esos desempates. [Manipulación de datos en CQL](https://cassandra.apache.org/doc/latest/cassandra/developing/cql/dml.html).

### 4. Qué pasa cuando los relojes no coinciden

Pongamos un ejemplo: un cliente con el reloj adelantado escribe `ciudad = Puebla`. Después, otro cliente escribe `ciudad = Oaxaca`, pero le asigna un timestamp menor. Aunque Oaxaca se escribió después en tiempo real, Puebla puede seguir siendo el valor ganador. El ejemplo muestra por qué generar marcas crecientes en un solo cliente no resuelve las diferencias entre clientes independientes. [Timestamps en Cassandra](https://docs.datastax.com/en/dse/6.8/drivers/timestamps.html).

Se recomienda sincronizar los relojes con **NTP**, que reduce sus diferencias. La reconciliación sigue comparando timestamps; no espera a descubrir cuál escritura ocurrió realmente después. [Arquitectura de Cassandra](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html).

## Google Spanner

### 1. Qué es y para qué se usa

Google Spanner es una base de datos distribuida que permite utilizar SQL y realizar transacciones con datos repartidos entre servidores. Se usa cuando una aplicación necesita crecer y mantener consistencia en operaciones que involucran varios datos, incluso entre centros de datos. [Artículo de Spanner](https://storage.googleapis.com/gweb-research2023-media/pubtools/1974.pdf).

### 2. Qué tiempo usa para ordenar

Spanner utiliza **tiempo físico con incertidumbre explícita**, mediante un servicio llamado **TrueTime**. En lugar de devolver una hora como si fuera exacta, `TT.now()` devuelve un intervalo:

```text
[earliest, latest]
```

La garantía es que el instante real de la consulta se encuentra dentro de ese intervalo. Por ejemplo, si devolviera `[10:00:00.100, 10:00:00.106]`, representaría un margen de incertidumbre entre ambos extremos. La infraestructura utiliza referencias GPS y relojes atómicos para mantener pequeño ese margen. Spanner aprovecha esta información para asignar marcas de confirmación a las transacciones. [TrueTime y confirmación de transacciones](https://cloud.google.com/blog/products/databases/strict-serializability-and-external-consistency-in-spanner).

### 3. Qué orden garantiza y hasta dónde

Con el aislamiento **serializable**, que es el predeterminado, Spanner ofrece **consistencia externa**. Esto significa que las transacciones se comportan como si se ejecutaran una por una, respetando además el orden observable en tiempo real. Si una transacción A termina antes de que comience una B, A debe quedar antes que B. La garantía abarca las transacciones de una base de datos, incluso cuando sus datos están distribuidos entre regiones. [TrueTime y consistencia externa](https://docs.cloud.google.com/spanner/docs/true-time-external-consistency).

Por ejemplo, si una aplicación espera la confirmación de un depósito antes de iniciar un retiro, el orden debe respetar esa dependencia. Para transacciones concurrentes, Spanner establece un orden compatible con sus garantías; no necesita afirmar que una ocurrió físicamente antes cuando se superponen. Esta garantía corresponde al aislamiento serializable; no debe atribuirse sin más a otros niveles de aislamiento. [TrueTime y consistencia externa](https://docs.cloud.google.com/spanner/docs/true-time-external-consistency).

### 4. Qué pasa cuando los relojes no coinciden

Spanner contempla esas diferencias dentro del intervalo de TrueTime. Durante la confirmación utiliza una espera llamada **commit wait**: antes de comunicar el éxito, debe poder asegurar que la marca elegida ya quedó en el pasado. La condición se puede expresar como:

```text
TT.now().earliest > timestamp_de_confirmacion
```

Así evita confirmar una transacción cuya marca todavía podría estar en el futuro. Esta espera puede transcurrir mientras se realiza la replicación. [TrueTime y confirmación de transacciones](https://cloud.google.com/blog/products/databases/strict-serializability-and-external-consistency-in-spanner).

Si aumenta la incertidumbre, puede aumentar el tiempo necesario para confirmar. La garantía depende de que el intervalo represente correctamente ese margen. TrueTime trabaja junto con los protocolos de transacciones y la replicación por consenso; el reloj por sí solo no proporciona toda la consistencia. [Artículo de Spanner](https://storage.googleapis.com/gweb-research2023-media/pubtools/1974.pdf).

## Diferencia entre las tres tecnologías

| Tecnología | Mecanismo utilizado | Alcance del orden | Si los relojes difieren |
| --- | --- | --- | --- |
| Kafka | Número de secuencia: offset | Dentro de una partición | Conserva el orden de offsets; las marcas de tiempo pueden diferir |
| Cassandra | Timestamps basados habitualmente en tiempo físico | Resolución de versiones de una misma celda | Puede ganar una escritura con el reloj adelantado; necesita sincronización |
| Spanner | Tiempo físico con incertidumbre: TrueTime | Transacciones de una base de datos con aislamiento serializable | Considera el margen de error y espera cuando hace falta |

La diferencia principal está en qué significa “ir después”. En Kafka significa ocupar una posición posterior en la partición. En Cassandra significa tener una marca mayor para resolver versiones del mismo dato. En Spanner, las marcas y el protocolo de transacciones permiten respetar también el orden observable de las confirmaciones. Estas diferencias se desarrollan en el [diseño de Kafka](https://kafka.apache.org/42/design/design/), la [arquitectura de Cassandra](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html) y la [documentación de consistencia externa de Spanner](https://docs.cloud.google.com/spanner/docs/true-time-external-consistency).

## Fuentes

- [Apache Kafka – Introduction](https://kafka.apache.org/intro/): eventos, productores, consumidores, topics y particiones.
- [Apache Kafka – Design](https://kafka.apache.org/42/design/design/): offsets, orden del registro y replicación mediante líderes de partición.
- [Apache Kafka – Topic Configs](https://kafka.apache.org/42/configuration/topic-configs/): `CreateTime`, `LogAppendTime` y límites para aceptar timestamps.
- [Apache Kafka – Producer Configs](https://kafka.apache.org/42/configuration/producer-configs/): idempotencia, reintentos y conservación del orden de envío.
- [Apache Cassandra – Dynamo](https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html): arquitectura distribuida, Last Write Wins, resolución por columna y sincronización con NTP.
- [Apache Cassandra Java Driver – Query Timestamps](https://apache.github.io/cassandra-java-driver/4.18.0/core/query_timestamps/): marcas de escritura en microsegundos y generación monotónica local.
- [Apache Cassandra – Data Manipulation](https://cassandra.apache.org/doc/latest/cassandra/developing/cql/dml.html): timestamps de las operaciones y límites del orden en un `BATCH`.
- [DataStax – Query timestamps in Cassandra drivers](https://docs.datastax.com/en/dse/6.8/drivers/timestamps.html): reglas de desempate y efectos de las diferencias entre relojes.
- [Google Cloud – TrueTime and external consistency](https://docs.cloud.google.com/spanner/docs/true-time-external-consistency): alcance de la consistencia externa y aislamiento serializable.
- [Google Cloud – Strict serializability and external consistency in Spanner](https://cloud.google.com/blog/products/databases/strict-serializability-and-external-consistency-in-spanner): intervalos de TrueTime, referencias de tiempo y commit wait.
- [Spanner: Google's Globally Distributed Database](https://storage.googleapis.com/gweb-research2023-media/pubtools/1974.pdf): funcionamiento de Spanner, incertidumbre del reloj y coordinación de transacciones distribuidas.
