# T04 · ¿Qué reloj usan las tecnologías que conoces?

Josué Hernández Rubio · Sistemas Distribuidos · Grupo 2

## Apache Kafka

Kafka recibe y guarda eventos para que otros servicios los consuman. Un ejemplo es guardar los cambios de estado de los pedidos de una tienda.

El líder de cada partición agrega los registros a su historial y les asigna un número llamado *offset*. Dentro de esa partición, el registro con offset 15 está antes que el 16. Cada partición lleva su propia secuencia, así que dos registros con offset 15 en particiones distintas son independientes para este orden.

Kafka usa la posición en el historial para leer los eventos en orden. Por eso, aunque dos productores tengan horas distintas, el líder sigue asignando los offsets según cómo agrega los registros. La hora que trae cada evento se puede usar en los análisis que haga la aplicación.

## Apache Cassandra

Cassandra es una base de datos distribuida que reparte datos y mantiene copias en varios nodos. Sirve, por ejemplo, para guardar grandes volúmenes de mediciones.

Cada escritura normal lleva una marca de tiempo en microsegundos. Puede asignarla el cliente o, en su ausencia, el nodo que coordina la escritura usando su reloj de pared. Si dos escrituras tienen marcas distintas y modifican la misma columna de la misma fila, prevalece la marca más alta. Este orden sirve para elegir qué versión queda en esa columna. Cada columna puede tener su propia marca.

Los equipos deben sincronizar sus relojes, por ejemplo con NTP. Si un equipo va adelantado, una escritura que hizo antes puede quedar con una marca mayor y prevalecer sobre otra que ocurrió después.

## CockroachDB

CockroachDB es una base de datos SQL distribuida. Permite que una transacción lea o modifique datos repartidos en varios nodos.

Cada nodo lleva un reloj lógico híbrido (HLC). Combina la hora física con un contador lógico y se ajusta al recibir marcas de otros nodos. La base usa esas marcas para decidir qué versiones de los datos ve una transacción. Con el aislamiento `SERIALIZABLE`, el resultado de las transacciones confirmadas debe ser equivalente a ejecutarlas una por una, incluso si usan datos de distintos nodos.

CockroachDB admite un desfase limitado entre relojes. Si una lectura encuentra una versión cuya marca queda en la ventana de incertidumbre, la transacción puede avanzar su marca de tiempo y reintentar. Por eso mantener los relojes sincronizados ayuda a reducir reintentos.

## Opcional · Reloj de Lamport en Java

Simulé tres servidores que intercambian cuatro mensajes. Cada servidor empieza en 0. Un evento local o un envío suma 1. Al recibir aplica `max(reloj local, reloj del mensaje) + 1`.

En la prueba, A envía el pedido con reloj 2 y B lo recibe con reloj 3. B manda la reserva con 4 y C la recibe con 5. La confirmación pasa de C a A de 6 a 7, y el cierre pasa de A a B de 8 a 9. Cada recepción queda después de su envío. A y C también tienen un evento local con reloj 1. Esos dos eventos son independientes.

- [Código Java](lamport/Lamport.java)
- [Salida de la prueba](lamport/salida.txt)

## Fuentes

- Apache Kafka. [Introducción](https://kafka.apache.org/43/getting-started/introduction/) y [diseño de Kafka](https://kafka.apache.org/43/design/design/).
- Apache Cassandra. [Particionamiento y versiones de datos](https://cassandra.apache.org/doc/stable/cassandra/architecture/dynamo.html) y [operaciones de escritura en CQL](https://cassandra.apache.org/doc/latest/cassandra/developing/cql/dml.html).
- Cockroach Labs. [Gestión de relojes en CockroachDB](https://www.cockroachlabs.com/blog/clock-management-cockroachdb/) y [artículo sobre la arquitectura de CockroachDB](https://www.cockroachlabs.com/pdf/cockroachdb-the-resilient-geo-distributed-sql-database-sigmod-2020.pdf).
- Leslie Lamport (1978). [*Time, Clocks, and the Ordering of Events in a Distributed System*](https://lamport.azurewebsites.net/pubs/time-clocks.pdf).
