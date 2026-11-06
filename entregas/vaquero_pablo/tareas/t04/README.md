# T04 · Qué reloj usan Kafka, Redis Streams y Spanner

Pablo Vaquero · Sistemas Distribuidos · Grupo 2 · 6 de noviembre de 2026

Ordenar no siempre significa comparar horas. Elegí tres casos: una posición en un log, un identificador con tiempo y secuencia, y una base que incorpora la incertidumbre del reloj físico.

## 1. Apache Kafka

**Qué es.** Es una plataforma de eventos: los productores escriben en topics y los consumidores leen su historial. Cada topic se divide en particiones.

**Qué usa para ordenar.** Cada registro ocupa un **offset**, una posición asignada dentro del log de su partición. Es una secuencia, no un reloj físico ni un reloj vectorial. Un timestamp del evento no sustituye al offset.

**Qué garantiza y hasta dónde.** El log establece orden dentro de cada partición. No hay un único orden entre particiones: el offset 20 de una no se puede comparar con el 19 de otra para decidir cuál ocurrió primero. En una tienda usaría `pedidoId` como llave para reunir los eventos del mismo pedido en una partición. La aplicación todavía debe cuidar que su procesamiento paralelo no deshaga ese orden.

**Si los relojes no coinciden.** El orden del log no depende de igualar los relojes de los productores. Si uno manda una fecha atrasada, el registro sigue teniendo su posición de llegada al log. Eso tampoco reconstruye automáticamente el orden causal que había antes de publicarlo.

Fuente: [Apache Kafka 4.1, Design](https://kafka.apache.org/41/design/design/).

## 2. Redis Streams

**Qué es.** Es una estructura de Redis para añadir y consumir entradas de un flujo. `XADD` agrega una entrada y devuelve su identificador.

**Qué usa para ordenar.** Con `XADD flujo * ...`, Redis genera un ID con la forma `milisegundos-secuencia`. La primera parte parte del reloj de pared del servidor; la segunda distingue entradas con el mismo componente temporal. Es una combinación de tiempo físico y secuencia local, no un reloj vectorial ni el protocolo híbrido de un clúster distribuido.

**Qué garantiza y hasta dónde.** Los IDs añadidos crecen dentro de **ese stream**. Dos streams distintos no establecen por sus IDs una relación causal global. Tampoco indican que todos los consumidores terminaron de procesar en ese orden.

**Si los relojes no coinciden.** Si el reloj del servidor retrocede, Redis conserva como componente temporal el del último ID cuando este es mayor, y aumenta la secuencia. Así evita retroceder dentro del stream. Si el tiempo avanza mucho, el ID puede avanzar con él: que sea creciente no lo convierte en la hora real exacta del evento. Un ID explícito debe ser mayor que el último existente.

Fuente: [Redis, XADD: generación de identificadores](https://redis.io/docs/latest/commands/xadd/).

## 3. Google Spanner y TrueTime

**Qué es.** Spanner es una base distribuida con transacciones y versiones de los datos. TrueTime es el servicio de tiempo que utiliza para asignar timestamps con garantías entre servidores.

**Qué usa para ordenar.** TrueTime representa el tiempo físico como un **intervalo de incertidumbre**, en lugar de fingir que todas las máquinas conocen un instante idéntico. Spanner combina esos límites con su protocolo transaccional; no basta con ordenar valores de `Date.now()`.

**Qué garantiza y hasta dónde.** Con aislamiento serializable ofrece consistencia externa para las transacciones de la base, incluso entre particiones: el orden de las transacciones respeta el orden observable de su confirmación. Si una operación termina antes de que otra comience, ese orden no debe invertirse en la historia transaccional. No es una promesa de orden para cualquier mensaje externo a la base.

**Si los relojes no coinciden.** TrueTime utiliza referencias temporales y comunica el margen de incertidumbre. El protocolo puede esperar a que el tiempo de commit elegido haya quedado definitivamente en el pasado (*commit wait*) antes de comunicar éxito. El costo incluye espera y coordinación: la sincronización no elimina mágicamente la incertidumbre.

Fuentes: [Spanner, TrueTime y consistencia externa](https://docs.cloud.google.com/spanner/docs/true-time-external-consistency) y [Corbett et al., Spanner, secciones 3 y 4](https://research.google/pubs/spanner-googles-globally-distributed-database-2/).

## Comparación

| Tecnología | Mecanismo de orden | Alcance |
| --- | --- | --- |
| Kafka | Offset del log | Una partición |
| Redis Streams | Milisegundos y secuencia creciente | Un stream |
| Spanner | TrueTime y protocolo transaccional | Transacciones de la base con aislamiento serializable |

Mi conclusión es que primero hay que preguntar qué orden necesita la operación. Para recorrer eventos de un pedido puede bastar una partición; para respetar el orden observable de transacciones distribuidas se necesitan garantías adicionales. Un número creciente por sí solo no demuestra causalidad.

## Opcional de Lamport

En `lamport/` hay una simulación Java de tres servidores lógicos y cuatro mensajes. Cada evento local y envío incrementa el reloj; cada recepción aplica `max(local, recibido) + 1`. El planificador es secuencial para repetir la misma salida, y el estado de cada servidor está separado. No simula red real ni usa la hora de pared. Las aserciones comprueban crecimiento por servidor y `envío < recepción` para los cuatro mensajes.

Con Java 21: `java -ea lamport/LamportDemo.java`. La salida de la ejecución está en `lamport/salida.txt`.

Fuentes consultadas el 6 de noviembre de 2026. Preparado con apoyo de Codex.
