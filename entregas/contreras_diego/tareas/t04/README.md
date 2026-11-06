CockroachDB



1\. ¿Qué es y para qué se usa?



CockroachDB es una base de datos SQL distribuida de código abierto diseñada para ofrecer alta disponibilidad, tolerancia a fallos a nivel de centros de datos y consistencia fuerte (serializable) a escala global. Se utiliza en aplicaciones empresariales críticas que exigen una fuerte consistencia transaccional (ACID) sin sacrificar la escalabilidad horizontal.



2\. ¿Qué tiempo usa para ordenar?



Utiliza Relojes Lógicos Híbridos (Hybrid Logical Clocks o HLC). Estos relojes combinan el tiempo físico obtenido mediante NTP con un componente lógico inspirado en los relojes de Lamport, asegurando que el HLC siempre sea mayor o igual al tiempo físico real para capturar relaciones causales.



3\. ¿Qué orden garantiza y hasta dónde?



Garantiza un orden global y consistente en todo el sistema bajo el nivel de aislamiento serializable. Esto permite que las transacciones y operaciones ACID se ejecuten de manera coordinada y ordenada a través de múltiples particiones (ranges) y nodos distribuidos geográficamente.



4\. ¿Qué pasa cuando los relojes no coinciden?



Maneja el sesgo temporal implementando una ventana de incertidumbre. Si una transacción intenta leer o escribir datos con marcas de tiempo adelantadas más allá de un umbral tolerado, el sistema obliga a la transacción a esperar el tiempo necesario o la reintenta automáticamente para preservar la consistencia causal.



Apache Kafka



1\. ¿Qué es y para qué se usa?



Apache Kafka es una plataforma de streaming de eventos distribuida de alto rendimiento. Se utiliza principalmente para la ingesta, almacenamiento desacoplado, publicación y procesamiento en tiempo real de flujos masivos de datos entre diferentes microservicios y sistemas empresariales.



2\. ¿Qué tiempo usa para ordenar?



No emplea relojes lógicos ni sincronización HLC para sus operaciones principales, sino que se basa en números de secuencia estrictos y monotónicos conocidos como offsets, asignados de manera incremental por el broker líder de cada partición.



3\. ¿Qué orden garantiza y hasta dónde?



Garantiza un orden estricto limitado estrictamente al ámbito de una partición individual. Kafka asegura que los mensajes enviados a una misma partición se lean y procesen exactamente en el orden secuencial en que fueron añadidos al registro de eventos.



4\. ¿Qué pasa cuando los relojes no coinciden?



Kafka se desentiende por completo de los relojes de pared o de las diferencias temporales entre los productores al ordenar los eventos principales. Ante discrepancias de tiempo, confía exclusivamente en el número de secuencia u offset asignado internamente, ignorando cualquier sesgo temporal externo.



MongoDB



1\. ¿Qué es y para qué se usa?



MongoDB es una base de datos NoSQL orientada a documentos diseñada para ofrecer gran flexibilidad en el almacenamiento de esquemas dinámicos, alto rendimiento operativo y replicación automática mediante conjuntos de réplicas y clústeres fragmentados (sharding).



2\. ¿Qué tiempo usa para ordenar?



Implementa el concepto de Cluster Time, el cual se sustenta en Relojes Lógicos Híbridos (HLC) sincronizados de manera continua a través del intercambio de mensajes y latidos entre los nodos del conjunto de réplicas.



3\. ¿Qué orden garantiza y hasta dónde?



Garantiza la consistencia causal a nivel de todo el clúster. Esto permite que las operaciones dependientes observen los efectos de operaciones anteriores sin importar a qué nodo específico se conecten, asegurando además un orden estricto dentro de cada colección y documento.



4\. ¿Qué pasa cuando los relojes no coinciden?



Utiliza el componente lógico del HLC para ajustar dinámicamente el tiempo del clúster hacia adelante cuando un nodo recibe una operación con una marca de tiempo superior a su reloj local, manteniendo así una línea de tiempo monotónica coherente sin necesidad de bloqueos de espera prolongados.



Bibliografía



\- Cockroach Labs. (2026). CockroachDB Architecture: Transaction Layer and Hybrid Logical Clocks. Recuperado de \[https://www.cockroachlabs.com/docs/stable/architecture/transaction-layer](https://www.cockroachlabs.com/docs/stable/architecture/transaction-layer)



\- Sookocheff, K. (2022). Hybrid Logical Clocks. Sookocheff.com. Recuperado de \[https://sookocheff.com/post/time/hybrid-logical-clocks/](https://sookocheff.com/post/time/hybrid-logical-clocks/)



\- Apache Software Foundation. (2026). Apache Kafka Documentation: Producers, Consumers, and Log Compaction. Recuperado de \[https://kafka.apache.org/documentation/](https://kafka.apache.org/documentation/)



\- MongoDB, Inc. (2026). MongoDB Manual: Replication, Cluster Time, and Causal Consistency. Recuperado de \[https://www.docs.mongodb.com/manual/core/read-isolation-consistency-recency/](https://www.google.com/search?q=https://www.docs.mongodb.com/manual/core/read-isolation-consistency-recency/)

