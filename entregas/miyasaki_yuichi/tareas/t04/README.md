# T04 · ¿Qué reloj usan las tecnologías que conoces?

Para esta tarea elegí tres tecnologías que resuelven el orden de formas muy distintas:

- Apache Kafka: No usa reloj para ordenar: un líder asigna un número de secuencia.
- Apache Cassandra: Usa el reloj de pared tal cual y confía en él, con la regla last-write-wins.
- CockroachDB: Usa un reloj híbrido, físico más lógico, y acota el error del reloj.

---

## 1. Apache Kafka

### 1.1 Qué es y para qué se usa

Kafka es una plataforma de streaming de eventos que funciona como un log distribuido: los productores escriben mensajes al final y los consumidores los leen a su ritmo. Los mensajes se agrupan en topics, y cada topic se divide en particiones replicadas en varios brokers, con un broker líder por partición. Se usa para conectar microservicios por eventos, armar pipelines de datos, centralizar logs y para event sourcing.

### 1.2 Qué tiempo usa para ordenar

Un número de secuencia que asigna alguien: el offset. Cuando un mensaje llega al líder de la partición, este le pone el siguiente número disponible: 0, 1, 2 y así sucesivamente. Ese número es el orden y no interviene ningún reloj.

Los mensajes también traen un timestamp. Lo puede poner el productor con su reloj, que es el valor por defecto y se llama CreateTime, o el broker al escribir el mensaje, que se llama LogAppendTime. Ese timestamp sirve para la retención por tiempo, para buscar mensajes por fecha y para las ventanas de Kafka Streams, pero no para ordenar.

Kafka usa además otros contadores lógicos: el productor idempotente numera sus mensajes para que el broker detecte duplicados, y el leader epoch sube cada vez que cambia el líder de una partición, parecido al term de Raft.

### 1.3 Qué orden garantiza y hasta dónde

- Dentro de una partición: orden total. Todos los consumidores ven los mensajes en el mismo orden de offset.
- Por llave: los mensajes con la misma llave caen en la misma partición, así que quedan ordenados entre sí. Esto se rompe si se cambia el número de particiones.
- Por productor: con el productor idempotente, activado por defecto desde Kafka 3.0, los reintentos no desordenan ni duplican mensajes.
- Entre particiones: ninguno. Para tener orden global hay que usar una sola partición y perder el paralelismo.

### 1.4 Qué pasa cuando los relojes no coinciden

Para el orden no importa, porque el offset lo decide un solo nodo y no hay relojes que comparar. El desfase solo afecta al timestamp: con CreateTime cada productor usa la hora de su máquina, así que los timestamps de una partición pueden ir hacia atrás aunque los offsets avancen. Un reloj muy desfasado también afecta la retención, porque un mensaje con fecha muy vieja puede hacer que su segmento se borre antes de tiempo. Desde Kafka 3.6, con KIP-937, el broker puede rechazar mensajes cuyo timestamp se aleje demasiado de su propia hora, aunque por defecto ese límite es prácticamente infinito.

---

## 2. Apache Cassandra

### 2.1 Qué es y para qué se usa

Cassandra es una base de datos NoSQL distribuida sin líder: todos los nodos son iguales y los datos se reparten en un anillo, con un diseño heredado de Dynamo de Amazon. Cada escritura se replica en varios nodos y el cliente elige cuántas réplicas deben responder, por ejemplo ONE o QUORUM. Se usa cuando hay muchísimas escrituras y se necesita alta disponibilidad: series de tiempo, IoT, historial de mensajes o eventos de usuario.

### 2.2 Qué tiempo usa para ordenar

Reloj físico de pared. Cada valor de cada columna se guarda con un timestamp en microsegundos desde 1970. Lo pone el driver del cliente, el nodo coordinador si el cliente no lo manda, o la aplicación con USING TIMESTAMP. El driver de Java intenta que sus timestamps nunca retrocedan, pero eso solo vale dentro de un cliente; entre clientes no hay coordinación.

No usa relojes de Lamport ni vectoriales. Dynamo sí usaba relojes vectoriales, pero Cassandra eligió algo más simple: quedarse con el timestamp más alto.

### 2.3 Qué orden garantiza y hasta dónde

Cassandra no ordena operaciones, solo resuelve conflictos con last-write-wins por celda:

- Si dos réplicas tienen versiones distintas del mismo valor, gana la del timestamp mayor y la otra se descarta.
- Si los timestamps empatan, gana el borrado; si los dos son valores normales, gana el de bytes mayores, para que todas las réplicas lleguen al mismo resultado.
- Las filas de una partición se guardan ordenadas por la clustering key que define el diseñador, no por el orden de llegada.
- Las lightweight transactions, como IF NOT EXISTS, usan Paxos y sí dan un orden linealizable, pero solo dentro de una partición y con más costo.

### 2.4 Qué pasa cuando los relojes no coinciden

Lo ignora: confía ciegamente en el timestamp, sin esperar ni corregir nada. Por eso exige NTP en todos los nodos y clientes. Si un reloj se desfasa, pueden pasar cosas como estas:

- Escrituras perdidas: si el cliente A tiene el reloj 2 s adelantado, su escritura le gana a una que el cliente B hizo 1 s después. Cassandra le responde "OK" a B, pero su valor no se vuelve a ver.
- Borrados que no borran: un DELETE desde un cliente atrasado queda con un timestamp menor que el dato, y el dato sigue apareciendo.

Kyle Kingsbury mostró con sus pruebas de Jepsen que, con particiones de red, esto puede perder muchas escrituras confirmadas aunque se use QUORUM.

---

## 3. CockroachDB

### 3.1 Qué es y para qué se usa

CockroachDB es una base de datos SQL distribuida compatible con PostgreSQL. Divide los datos en rangos y replica cada uno con Raft, y ofrece transacciones serializables aunque toquen datos en distintos nodos o regiones. Está inspirada en Google Spanner, pero funciona en servidores comunes, sin relojes atómicos. Se usa para pagos, inventarios y aplicaciones multi-región que necesitan SQL y tolerar caídas.

### 3.2 Qué tiempo usa para ordenar

Un híbrido: Hybrid Logical Clock, o HLC. Cada timestamp tiene una parte física, cercana a la hora de pared, y una parte lógica, un contador que desempata eventos con el mismo tiempo físico. Cada mensaje entre nodos lleva el HLC del emisor, y el receptor adelanta su reloj al mayor de los dos, igual que en la regla de Lamport. Así, si A causa B, el timestamp de A es menor que el de B aunque los relojes físicos difieran un poco. Cada transacción recibe un timestamp HLC y sus escrituras se guardan como versiones con ese timestamp.

### 3.3 Qué orden garantiza y hasta dónde

- Serializable en todo el clúster: el resultado equivale a ejecutar las transacciones una tras otra, en el orden de sus timestamps.
- Linealizable por llave: si una transacción escribió una llave y terminó, cualquier lectura posterior de esa llave ve el valor nuevo, siempre que los relojes estén dentro del límite.
- Lo que no garantiza: el orden real entre transacciones que tocan llaves distintas. Una transacción que empezó después puede quedar con un timestamp menor si su nodo tiene el reloj atrasado. Spanner sí garantiza ese orden.

### 3.4 Qué pasa cuando los relojes no coinciden

CockroachDB acota el error del reloj:

1. Sincroniza con NTP y define un desfase máximo, max-offset, de 500 ms por defecto.
2. Si una transacción lee un valor cuyo timestamp cae dentro de esa ventana de incertidumbre, no puede saber si ocurrió antes o después. En vez de adivinar, sube su timestamp por encima de ese valor o reinicia la transacción.
3. Los nodos comparan sus relojes, y si uno está desfasado más del 80 % del máximo respecto de al menos la mitad de los demás, se apaga solo para no romper la consistencia.

Spanner lo resuelve distinto: usa GPS y relojes atómicos con TrueTime, que tiene una incertidumbre de pocos milisegundos, y espera a que pase esa incertidumbre antes de confirmar cada transacción. CockroachDB no puede esperar cientos de milisegundos en cada commit, así que en vez de esperar al escribir, a veces reintenta al leer.

---

## Mi conclusion sobre estas tres tecnologias.

Las tres tecnologías resuelven distinto el problema del reloj. Kafka lo evita dándole a un solo nodo la tarea de numerar. Cassandra acepta el riesgo del reloj de pared a cambio de velocidad y disponibilidad, y depende de que NTP funcione bien. CockroachDB mezcla la idea de Lamport con el reloj físico, acota cuánto puede equivocarse y paga con reintentos cuando no está segura.

---

## Fuentes

Kafka
- Apache Kafka Documentation. https://kafka.apache.org/documentation/
- KIP-32: Add timestamps to Kafka message. https://cwiki.apache.org/confluence/display/KAFKA/KIP-32+-+Add+timestamps+to+Kafka+message
- KIP-98: Exactly Once Delivery and Transactional Messaging. https://cwiki.apache.org/confluence/display/KAFKA/KIP-98+-+Exactly+Once+Delivery+and+Transactional+Messaging
- KIP-101: Alter Replication Protocol to use Leader Epoch rather than High Watermark for Truncation. https://cwiki.apache.org/confluence/display/KAFKA/KIP-101+-+Alter+Replication+Protocol+to+use+Leader+Epoch+rather+than+High+Watermark+for+Truncation


Cassandra
- Apache Cassandra Documentation: arquitectura Dynamo. https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html
- Apache Cassandra Documentation: Data Manipulation. https://cassandra.apache.org/doc/latest/cassandra/developing/cql/dml.html
- DataStax Java Driver 4.17: Query timestamps. https://docs.datastax.com/en/developer/java-driver/4.17/manual/core/query_timestamps/
- Código fuente de Cassandra: Cells.java. https://github.com/apache/cassandra/blob/trunk/src/java/org/apache/cassandra/db/rows/Cells.java
- Kingsbury, K. 2013. Jepsen: Cassandra. https://aphyr.com/posts/294-call-me-maybe-cassandra

CockroachDB y Spanner
- CockroachDB Docs: Transaction Layer. https://www.cockroachlabs.com/docs/stable/architecture/transaction-layer
- CockroachDB Docs: Production Checklist, sección Clock synchronization. https://www.cockroachlabs.com/docs/stable/recommended-production-settings
- Cockroach Labs Blog. Living Without Atomic Clocks. https://www.cockroachlabs.com/blog/living-without-atomic-clocks/

