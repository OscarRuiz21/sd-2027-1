# T04 · ¿Qué reloj usan las tecnologías que conoces?

### 1. Amazon DynamoDB

*   **Qué es y para qué se usa:** Es una base de datos NoSQL gestionada por AWS, con modelo de tablas clave-valor/documento y latencia de milisegundos a cualquier escala. Alimenta Alexa, los sitios de Amazon.com y los centros de distribución de Amazon. Se usa en carritos de compra, perfiles de usuario, videojuegos y aplicaciones serverless. No debe confundirse con Dynamo (paper de 2007, con relojes vectoriales): DynamoDB comparte casi todo el nombre, pero poco de su arquitectura.
*   **Qué tiempo usa para ordenar:** Usa dos mecanismos distintos:
    *   **Orden lógico por partición.** Las réplicas de una partición forman un grupo de replicación que usa Multi-Paxos para elegir líder y llegar a consenso. El líder ordena las escrituras en su log, y solo él atiende escrituras y lecturas fuertemente consistentes.
    *   **Timestamps para transacciones.** Las transacciones se implementaron con un protocolo de ordenamiento por timestamp. El coordinador asigna a la transacción un timestamp de su reloj local, y el nodo de almacenamiento rechaza la operación si ya confirmó una transacción posterior sobre los mismos ítems.
*   **Qué orden te garantiza y hasta dónde:**
    *   Para un solo ítem: las escrituras quedan ordenadas por el líder y, con lectura fuertemente consistente, siempre ves la última escritura confirmada.
    *   Entre ítems: sí hay orden con TransactWriteItems y TransactGetItems; las transacciones dan garantías ACID dentro de la región.
    *   Entre regiones (Global Tables): consistencia eventual por defecto (MREC). Existe también el modo MRSC (consistencia fuerte multirregión), que no soporta las APIs de transacciones.
*   **Qué pasa cuando los relojes no coinciden:** Depende del mecanismo. El reloj físico sí importa en tres lugares:
    *   **Transacciones:** relojes mejor sincronizados producen más transacciones exitosas, porque el orden en tiempo real coincide mejor con el orden de serialización. Además, hay salvaguardas para ignorar relojes adelantados por muchos segundos y aislar a los coordinadores con relojes defectuosos.
    *   **Global Tables (MREC):** los conflictos se resuelven con "last writer wins" según el timestamp de la escritura. Cada ítem guarda un timestamp de última escritura, y la escritura entrante solo se aplica si su timestamp es mayor. Con relojes desfasados, una escritura anterior en tiempo real puede ganarle a una posterior.
    *   **TTL:** borra automáticamente un registro cuando llega la hora programada.

    Para las operaciones de un solo ítem dentro de una región, el orden lo da el líder de la partición, no el reloj.

### 2. MongoDB

*   **Qué es y para qué se usa:** Es la base de datos NoSQL orientada a documentos (formato BSON/JSON). Se usa para aplicaciones web, catálogos flexibles de productos y sistemas que requieren esquemas de datos dinámicos.
*   **Qué tiempo usa para ordenar:** Para su registro de operaciones (Oplog) y transacciones distribuidas, usa el Cluster Time, un Reloj Lógico Híbrido basado en el reloj de Lamport. Se representa como un valor de 64 bits: los 32 bits superiores son el tiempo físico (en segundos) y los 32 bits inferiores son un contador lógico (secuencia incremental).
*   **Qué orden te garantiza y hasta dónde:** Garantiza consistencia causal dentro de una sesión de cliente. Si un cliente lee un dato y luego escribe otro basándose en esa lectura, el reloj compartido en la sesión garantiza que el sistema respetará ese orden lógico de eventos. Esta garantía se cumple solo si las lecturas y escrituras de la sesión usan readConcern: "majority" y writeConcern: "majority".
*   **Qué pasa cuando los relojes no coinciden:** Como los nodos intercambian sus relojes en cada mensaje, el reloj lógico siempre avanza al valor mayor recibido (estilo Lamport). Si los relojes físicos están desfasados, el componente lógico del Cluster Time absorbe el problema empujando el contador hacia adelante, de modo que el orden no se rompe aunque el reloj del sistema operativo de un nodo se atrase. El otro lado del problema es que si el reloj de un nodo se adelanta demasiado, ese valor se propaga a todo el cluster; por eso existe un límite de deriva aceptable (maxAcceptableLogicalClockDriftSecs).

### 3. PostgreSQL

*   **Qué es y para qué se usa:** Es un sistema de gestión de bases de datos relacionales de código abierto, con cumplimiento ACID y control de concurrencia multiversión (MVCC). Se usa en cualquier sistema que necesite modelo relacional clásico y alta fiabilidad, en un solo nodo o con arquitectura primario-réplicas.
*   **Qué tiempo usa para ordenar:** No usa reloj físico para el control de concurrencia, sino identificadores y snapshots:
    *   **XID:** Postgres solo asigna un XID a una transacción cuando empieza a modificar datos. Antes de eso, el backend le da un ID virtual, compuesto por el identificador del proceso y un número secuencial. El XID lo toma el propio backend de un contador compartido; no hay un "proceso maestro" que lo asigne.
    *   **Snapshot:** se representa como xmin:xmax:lista_de_transacciones_activas. La visibilidad de cada versión de fila se decide con esa información, no solo comparando XIDs.
*   **Qué orden te garantiza y hasta dónde:** Depende del nivel de aislamiento. El XID por sí solo no define el orden de commit, solo el orden en que se asignó.
    *   **Read Committed (default):** una sentencia solo ve filas confirmadas antes de que ella empezara. Dos sentencias de la misma transacción pueden ver datos distintos.
    *   **Serializable:** si un patrón de lecturas y escrituras produciría un resultado imposible en cualquier ejecución serial, una de las transacciones se revierte con serialization_failure. Solo aquí tienes aislamiento completo.
    *   El alcance es una sola instancia. Con réplicas de lectura, estas pueden ir con retraso respecto al primario.
*   **Qué pasa cuando los relojes no coinciden:** Para el control de concurrencia, el reloj de pared no interviene, así que un salto en el reloj del sistema no rompe el aislamiento. Sí importa en otros casos:
    *   **NOW() y columnas con timestamps:** valen lo que valga el reloj del servidor.
    *   **track_commit_timestamp:** registra la hora de commit de cada transacción, pero solo de las confirmadas después de activarlo.
    *   **PITR (recovery_target_time):** recupera hasta una hora de commit, así que depende de esos timestamps.

## Referencias

* Elhemali et al., "Amazon DynamoDB: A Scalable, Predictably Performant, and Fully Managed NoSQL Database Service", USENIX ATC 2022. https://www.usenix.org/conference/atc22/presentation/elhemali
* Idziorek et al., "Distributed Transactions at Scale in Amazon DynamoDB", USENIX ATC 2023. https://www.usenix.org/system/files/atc23-idziorek.pdf
* M. Brooker, "The DynamoDB paper", 2022. https://brooker.co.za/blog/2022/07/12/dynamodb.html
* A. Jesse Jiryu Davis, reseña de "Distributed Transactions at Scale in Amazon DynamoDB". https://emptysqua.re/blog/review-distributed-transactions-at-scale-dynamodb
* AWS Prescriptive Guidance, "Overview of global tables". https://docs.aws.amazon.com/prescriptive-guidance/latest/dynamodb-global-tables/overview.html
* M. Demirbas, "Distributed Transactions at Scale in Amazon DynamoDB" (resumen), 2023. http://muratbuffalo.blogspot.com/2023/08/distributed-transactions-at-scale-in.html
* MongoDB Manual, "Causal Consistency and Read and Write Concerns". https://www.mongodb.com/docs/manual/core/causal-consistency-read-write-concerns/
* PostgreSQL Documentation, "SET TRANSACTION". https://www.postgresql.org/docs/current/sql-set-transaction.html
* B. McCreary (Brandur), "How Postgres Makes Transactions Atomic". https://brandur.org/postgres-atomicity
* Postgres Professional, "MVCC and snapshots" (DBA2). https://edu.postgrespro.com/16/dba2-16-en/dba2_03_mvcc_snapshots.html
* PlanetScale, "PostgreSQL MVCC". https://planetscale.com/blog/postgresql-mvcc
* PostgreSQL Documentation, parámetro track_commit_timestamp (Server Configuration) y función pg_xact_commit_timestamp(). https://www.postgresql.org/docs/current/runtime-config-replication.html
* PostgreSQL Documentation, "Recovery Target Settings" (recovery_target_time). https://www.postgresql.org/docs/current/runtime-config-wal.html