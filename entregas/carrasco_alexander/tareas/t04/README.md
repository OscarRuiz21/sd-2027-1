# T04 · ¿Qué reloj usan las tecnologías que conozco?

## 1. Apache Kafka

**Qué es y para qué se usa.** Plataforma de streaming de eventos. Guarda los
mensajes en un log distribuido dividido en particiones. Se usa para mover
eventos entre servicios, procesar flujos de datos y desacoplar sistemas.

**Qué tiempo usa para ordenar.** Apache Kafka no usa relojes para ordenar los mensajes, 
sino que utiliza un número de secuencia llamado offset. 
Este offset funciona como un número de turno o de boleto (0, 1, 2, 3...) que el servidor principal (broker líder) le asigna a cada mensaje justo cuando entra al sistema.
Aunque los mensajes también pueden incluir una marca de tiempo o timestamp (que indica a qué hora los creó el productor o a qué hora los recibió el servidor), Kafka no toma en cuenta esa hora para ordenarlos. El timestamp solo se usa para cosas secundarias, como borrar mensajes viejos después de ciertos días o buscar datos por fecha; pero el orden real y oficial siempre lo manda el offset.

**Qué orden garantiza y hasta dónde.** Orden total dentro de una partición.
Entre particiones distintas no hay garantía. Si se usa una llave, todos los
mensajes con la misma llave caen en la misma partición, así que el orden
queda garantizado por llave.

**Qué pasa cuando los relojes no coinciden.** Se ignora para el orden, porque
el offset no depende de ningún reloj. Los timestamps solo sirven para cosas
como retención por tiempo o buscar mensajes por fecha, y ahí un reloj
desajustado puede dar resultados raros, pero no desordena el log.

## 2. Apache Cassandra

**Qué es y para qué se usa.** Base de datos NoSQL distribuida, sin líder,
pensada para escalar y estar siempre disponible (por ejemplo, datos de
sensores, mensajería, historiales).

**Qué tiempo usa para ordenar.** Apache Cassandra utiliza el reloj físico o de pared para ordenar las operaciones. 
Esto significa que cada vez que se guarda un dato, este se registra acompañado de una marca de tiempo (timestamp) medida en microsegundos, 
la cual es asignada habitualmente por el cliente o por el nodo coordinador que recibe la petición. 
Cuando el sistema recibe dos versiones diferentes de un mismo dato, compara sus marcas de tiempo y siempre se queda con la que tenga el número mayor; 
a este criterio de selección se le conoce como el principio de la última escritura gana (last write wins o LWW).

**Qué orden garantiza y hasta dónde.** Apache Cassandra no ofrece un orden global para todo el sistema, sino que únicamente decide qué valor gana evaluando celda por celda 
(es decir, cada columna individual dentro de una fila) basándose en su marca de tiempo. 
Si necesitas garantías de orden y consistencia más fuertes, el sistema cuenta con las transacciones ligeras o LWT, las cuales utilizan el algoritmo Paxos para lograr linealizabilidad. 
Esto último significa que el sistema se comporta como una sola máquina perfecta, asegurando que cualquier lectura que ocurra después de confirmada una escritura vea obligatoriamente el nuevo valor, 
siguiendo una línea de tiempo única y estrictamente dentro de los límites de una partición.

**Qué pasa cuando los relojes no coinciden.** Apache Cassandra no espera ni corrige los desfases por sí mismo. 
Si dos nodos o clientes tienen relojes distintos, una escritura más nueva puede terminar perdiendo ante una más vieja simplemente porque esta última tiene una marca de tiempo mayor. 
Si ocurre un empate exacto en los tiempos, el sistema desempata comparando directamente los valores de los datos. 
Por esta razón, se recomienda encarecidamente mantener los nodos sincronizados utilizando herramientas como NTP (Network Time Protocol) o Chrony, 
las cuales son programas encargados de actualizar y alinear automáticamente los relojes internos de los servidores con una fuente de tiempo confiable en la red, evitando así que las computadoras se adelanten o se atrasen y reduciendo el riesgo de errores en el orden de las escrituras

## 3. Google Spanner (TrueTime)

**Qué es y para qué se usa.** Base de datos relacional distribuida globalmente
de Google, con transacciones y consistencia fuerte a escala mundial.

**Qué tiempo usa para ordenar.** Google Spanner utiliza un reloj físico especial llamado TrueTime.
 En lugar de regresar un único instante exacto (lo cual es imposible debido a los pequeños desfases de las computadoras), 
 TrueTime devuelve un intervalo de tiempo formado por un rango entre el momento más temprano y el más tardío (earliest y latest), garantizando con total seguridad que la hora real se encuentra dentro de ese rango. 
 Para lograr esta alta precisión, el sistema se apoya físicamente en receptores GPS y relojes atómicos instalados directamente en los centros de datos de Google.

**Qué orden garantiza y hasta dónde.** Google Spanner garantiza consistencia externa en todo el sistema, lo que también se conoce como serializabilidad estricta.
 Esto significa que si una transacción termina por completo antes de que comience otra en cualquier parte del mundo, la marca de tiempo de la primera siempre será menor que la de la segunda.
 En términos sencillos, la serializabilidad estricta es el nivel más alto de orden: asegura que todas las operaciones de la base de datos global ocurran de forma impecable y secuencial, exactamente como si todo el sistema funcionara en una sola computadora y respetando rigurosamente el tiempo real del mundo físico.

**Qué pasa cuando los relojes no coinciden.** Google Spanner no ignora los posibles desfases de tiempo, sino que los toma en cuenta calculando el margen de error del reloj y haciendo una pausa estratégica conocida como commit wait. 
Con este procedimiento, el servidor coordinador responsable de confirmar una operación espera un breve momento (generalmente unos pocos milisegundos) hasta asegurarse por completo de que el tiempo asignado a esa transacción ya transcurrió en todos los servidores del mundo. De esta manera, el sistema acepta pagar un pequeño costo en velocidad o latencia a cambio de garantizar un orden de datos absolutamente correcto y sin fallas a nivel mundial.

## Comparación rápida

| Tecnología | Tipo de tiempo | Alcance del orden | Si hay desfase |
|---|---|---|---|
| Kafka | Número de secuencia (offset) | Partición / llave | Lo ignora |
| Cassandra | Reloj físico (LWW) | Por celda | Puede perder escrituras; se mitiga con NTP |
| Spanner | Reloj físico con intervalo | Todo el sistema | Espera (commit wait) |

## Opcional: reloj de Lamport
Ver carpeta `lamport/` (código, salida y explicación en `lamport/README.md`).

## Fuentes
1. Apache Kafka. *Documentation: Design (The Log) y configuración de timestamps.* https://kafka.apache.org/documentation/
2. Apache Cassandra. *Documentation: Data Modeling, timestamps y Lightweight Transactions.* https://cassandra.apache.org/doc/latest/
3. Corbett, J. et al. (2012). *Spanner: Google's Globally-Distributed Database.* OSDI '12.
4. Google Cloud. *Spanner: TrueTime and external consistency.* https://cloud.google.com/spanner/docs/true-time-external-consistency
5. Lamport, L. (1978). *Time, Clocks, and the Ordering of Events in a Distributed System.* Communications of the ACM.

 