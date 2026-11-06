# T04: ¿Qué reloj usan las tecnologías que conoces?

##Apache Kafka
**Qué es y para qué se usa:**
Es una plataforma distribuida de streaming de eventos. Se utiliza principalmente como un log inmutable y un message broker de alto rendimiento para arquitecturas orientadas a eventos, permitiendo que múltiples microservicios publiquen y consuman flujos de datos en tiempo real.

**Qué tiempo usa para ordenar:**
Kafka no confía en relojes físicos para el orden causal. Utiliza números de secuencia lógicos, conocidos como Offsets. El nodo líder de cada partición le asigna un número entero estrictamente incremental (0, 1, 2, 3...) a cada mensaje en el momento en que es escrito en el disco.

**Qué orden te garantiza y hasta dónde:**
Garantiza un orden estricto, pero únicamente dentro de una misma partición. Si envías datos a un topic que tiene 5 particiones, Kafka no te puede garantizar un orden global. Si necesitas que los eventos de un mismo usuario se procesen en orden, debes usar una llave (por ejemplo, el ID del usuario) para forzar que todos sus eventos caigan siempre en la misma partición.

**Qué pasa cuando los relojes no coinciden:**
Como el orden base depende puramente del offset lógico, la causalidad y el orden de los mensajes son inmunes al desfasaje de los relojes de pared. Los relojes físicos (sincronizados vía NTP) se usan de forma secundaria: determinan políticas de retención (ej. "borrar mensajes después de 7 días") o permiten buscar mensajes por fecha. Si los relojes físicos de los nodos no coinciden, podrías retener mensajes más tiempo del esperado, pero el orden lógico de la partición nunca se rompe ni requiere desempatar.

##Apache Cassandra
**Qué es y para qué se usa:**
Es una base de datos NoSQL distribuida (de familia de columnas), diseñada para ofrecer alta disponibilidad y escalabilidad masiva sin un nodo maestro (arquitectura masterless o peer-to-peer), ideal para cargas pesadas de escritura.

**Qué tiempo usa para ordenar:**
Usa exclusivamente relojes físicos de pared. Para manejar situaciones donde dos clientes modifican el mismo dato en diferentes nodos al mismo tiempo, Cassandra aplica la política de Last-Write-Wins (LWW o "la última escritura gana"), basándose en el timestamp (marca de tiempo) adjunto a la petición.

**Qué orden te garantiza y hasta dónde:**
Garantiza un orden basado en tiempo a nivel de columna o celda específica (por llave). No ofrece garantías de orden transaccional global ni causalidad estricta entre múltiples filas. Simplemente promete que, eventualmente, el dato con la marca de tiempo física más alta será el que permanezca en todos los nodos de la partición.

**Qué pasa cuando los relojes no coinciden:**
Este es el punto débil del modelo LWW. Si los relojes no coinciden, Cassandra lo ignora y sobrescribe el dato ciegamente. Si el Nodo A tiene su reloj atrasado 5 minutos y guarda un dato después de que el Nodo B guardó otro, el dato del Nodo A será descartado en la replicación porque su timestamp parecerá más viejo, aunque en la realidad física haya ocurrido después. Por esto, Cassandra exige que todos los servidores tengan el protocolo NTP configurado y monitoreado estrictamente; si hay desfasaje, hay pérdida silenciosa de datos recientes.

##CockroachDB
**Qué es y para qué se usa:**
Es una base de datos SQL distribuida, diseñada para sobrevivir a desastres (de ahí su nombre, cucaracha) y escalar horizontalmente, pero manteniendo las garantías ACID estrictas de bases de datos relacionales tradicionales como PostgreSQL.

**Qué tiempo usa para ordenar:**
Usa un modelo híbrido conocido como Relojes Lógicos Híbridos (HLC - Hybrid Logical Clocks). Este sistema combina el reloj físico local (sincronizado con NTP) con un contador lógico estilo Lamport. El timestamp tiene una parte física (milisegundos) y una parte lógica.

**Qué orden te garantiza y hasta dónde:**
Garantiza serializabilidad estricta; es decir, un orden global en todo el sistema. Te da la ilusión matemática de que todas las transacciones que ocurren en un clúster distribuido en múltiples países se están ejecutando una por una, de forma secuencial, en una sola máquina.

**Qué pasa cuando los relojes no coinciden:**
El HLC maneja el desfasaje de forma inteligente. Si el reloj físico se atrasa, el componente lógico (el contador) avanza automáticamente para asegurar que el nuevo evento siempre tenga un timestamp mayor al evento causal anterior, desempatando eventos que ocurren en el mismo milisegundo físico.
Sin embargo, CockroachDB define un umbral máximo de desfasaje físico permitido (por defecto, 500 milisegundos). Si detecta que el reloj físico de un servidor se desvió de los demás por más de 500 ms, el nodo hace un apagado de emergencia (se suicida) y abandona el clúster para no comprometer la integridad global de los datos. Adicionalmente, al leer datos, si el timestamp entra en la ventana de incertidumbre del reloj físico, la transacción espera o se reinicia hasta estar segura de leer la verdad absoluta.

---

## Referencias
* Apache Software Foundation. (s.f.). *Apache Kafka documentation*. Recuperado el 6 de noviembre de 2026, de https://kafka.apache.org/documentation/
* Apache Software Foundation. (s.f.). *Dynamo architecture*. Apache Cassandra Documentation. Recuperado el 6 de noviembre de 2026, de https://cassandra.apache.org/doc/stable/cassandra/architecture/dynamo.html
* Cockroach Labs. (s.f.). *Architecture: Transaction layer*. CockroachDB Documentation. Recuperado el 6 de noviembre de 2026, de https://www.cockroachlabs.com/docs/stable/architecture/transaction-layer
*Cockroach Labs. (2022, 27 de junio). *Living without atomic clocks: Where CockroachDB and Spanner diverge*. Cockroach Labs Blog. https://www.cockroachlabs.com/blog/living-without-a-single-point-of-truth/ 
* Kleppmann, M. (2017). *Designing data-intensive applications*. O'Reilly Media.
