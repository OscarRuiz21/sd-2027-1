# T04: Joshua Rodríguez Zamora



## 1. MongoDB



***1. Qué es y para qué se usa.***

MongoDB es un sistema de gestión de bases de datos no relacional (NoSQL) de código abierto que emplea documentos flexibles (BSON) en lugar de tablas y filas para procesar y almacenar diversas formas de datos. Ofrece una gran escalabilidad y flexibilidad con consultas e indexación avanzada



Se llega a utilizar en IoT (Internet de las cosas), aplicaciones móviles, arquitectura de pagos, gestión de contenidos, videojuegos, entre otras aplicaciones.



 ***2. Qué tiempo usa para ordenar: ¿reloj físico (de pared o monotónico), reloj lógico (estilo Lamport o vectorial), un híbrido, o un número de secuencia que asigna alguien?***



MongoDB utiliza un reloj hibrido lógico basado en un reloj físico de pared (tiempo Unix) además del reloj lógico de Lamport.  





***3. Qué orden te garantiza y hasta dónde: ¿dentro de una partición, de una llave, de un proceso, en todo el sistema?***



Para establecer el orden global de eventos a través de un sistema distribuido MongoDB tiene que hacerse responsable no sólo de conjuntos de replicas, sino que también de clústeres particionados, donde cada shard es un conjunto de replicas que contiene una partición de datos.



Para establecer el orden global y parcial de eventos para conjuntos de replica y clústeres particionados MongoDB implementa el reloj lógico hibrido que se basa en el reloj lógico de Lamport. Donde cada escritura que cambia el estado en el sistema se le asigna un tiempo a cuando es aplicado al flujo principal. A su vez, este tiempo puede ser comparado a través de los miembros de despliegue.



Cada participante en un cluster particionado deben mantener registro y mandar el valor de su ultimo tiempo en cada mensaje, permitiendo que cada nodo a través de las particiones pueda converger en una noción del último tiempo, o momento, haciendo un consenso. Los primarios usan el último tiempo lógico para asignar nuevos tiempos a escrituras subsecuentes, creando así un orden causal por cualquier serie o operación relacionada. 



***4. Qué pasa cuando los relojes no coinciden: ¿sincroniza con NTP, espera, desempata, lo ignora?***



Una de las primeras recomendaciones de MongoDB para mitigar el riesgo de desfase de horaro entre componentes es sincronizar los relojes del host con un demonio de tiempo compatible con NTP. Ya sea chrony o systemd-timesyncd. Además de que se puede controlar la cantidad de deriva de relojes aceptable entre los componentes. Sin embargo, se deja que el S.O. maneje el control del tiempo. En este caso, MongoDB sólo mandará advertencias o detendrá la comunicación si se supera el umbral de tolerancia configurado. 







## 2. CockroachDB



***1. Qué es y para qué se usa.***



Se trata de una base de datos distribuida SQL, hecha para aplicaciones nativas en la nube. Junta las ventajas tradicionales de bases de datos relacionales con la resiliencia y escalabilidad de sistemas distribuidos, haciéndola una plataforma perfecta para manejar entornos dinámicos de gran escala y con datos críticos.

Esto porque una sola instancia de CRDB puede escalar de una laptop a miles de servidores distribuidos alrededor del mundo. Datos globales con una sola base de datos lógica.


***2. Qué tiempo usa para ordenar: ¿reloj físico (de pared o monotónico), reloj lógico (estilo Lamport o vectorial), un híbrido, o un número de secuencia que asigna alguien?***

Cockroach utiliza un reloj híbrido lógico, cuyo sistema utiliza componentes tanto físicos como lógicos, aplicando marcas de tiempo a todas las transacciones para que el sistema sepa cuando ocurrieron y así ordenarlas adecuadamente.


***3. Qué orden te garantiza y hasta dónde: ¿dentro de una partición, de una llave, de un proceso, en todo el sistema?***


Garantiza serializabilidad estricta en todo el sistema, combinada con ordenamiento linealizable con grupos de consenso con RAFT por dentro de cada partición o llave. 

***4. Qué pasa cuando los relojes no coinciden: ¿sincroniza con NTP, espera, desempata, lo ignora?***

Igual que MongoDB, CockroachDB utiliza servidores NTP para utilizar un sistema de tiempos. Para evitar una mayor deriva entre dos nodos y su tiempo interno se aplica un valor máximo permitido para el offset, donde el valor máximo configurable es de 500 [mS], con esto revisamos y mantenemos segura la consistencia de la lectura de la base de datos, pues, en el momento que se presenta una desincronización en el tiempo de dos nodos, se detiene espontaneamente el nodo que presenta más del 80% de deriva con respecto al tiempo real.

Sin embargo, ante una diferencia entre tiempos menor al 80% los nodos llegan a pausarse y reintentar cada transacción hasta que el reloj físico dentro del reloj híbrido alcance o supere el tiempo. Buscando preservar la consistencia estricta con una reducida latencia.


## 3. PostgreSQL

***1. ¿Qué es y para qué se usa?***

Es un sistema de base de datos objeto-relacional de tipo código abierto que usa y extiende el lenguaje SQL con muchas caracteristicas que almacenan y escalan seguramente las cargas más complicadas y grandes de datos.

Tiene muchas características que están pensadas para ayudar a desarrolladores a construir aplicaciones, a los administradores permitirles proteger la integridad de los datos y construir entornos tolerantes a fallas, además de ayudar a manejar los datos sin importar el tamaño. 

Sus usos más comúnes son aplicaciones web, móviles, procesamiento de transacciones, análisis de datos y data warehousing, entre otras.

***2. Qué tiempo usa para ordenar: ¿reloj físico (de pared o monotónico), reloj lógico (estilo Lamport o vectorial), un híbrido, o un número de secuencia que asigna alguien?***

Utiliza un número de secuencia asignado por el sistema para ordenar y determinar la visibilidad de los eventos y transacciones. No utiliza ningún tipo de reloj lógico de un sistema distribuido convencional, si no que usa dos identificadores numéricos secuenciales:

XID (transacción ID): Un número de secuencia de 32 bits que el sistema asigna de forma incremental a cada transacción que modfiica la base de datos, por ejemplo:

Transacción A tiene el XID: 100
Transacción B tiene el XID: 101

Entonces PostgreSQL sabe cuál ocurrió primero según el orden.

LSN (Log Sequence Number): Es un entero de 64 bits que representa la posición de un byte dentro del WAL (Write-Ahead Log). Cada cambio en la base de datos genera un registro en el WAL con un LSN incremental. Esto se utiliza para ordenar las operaciones de replicación y recuperación ante fallos.

***3. Qué orden te garantiza y hasta dónde: ¿dentro de una partición, de una llave, de un proceso, en todo el sistema?***

A diferencia de sistemas distribuidos, PostgreSQL es un motor relacional, donde el orden se analiza en dos dimensiones: el orden de recuperación de filas (Consultas SQL) y el orden de ejecución/visibilidad de transacciones (Concurrencias)

En las consultas, PostgreSQL no garantiza ningún orden implícito en ningun alcance, a menos que se incluya explícitamente una cláusula ORDER BY. Las tablas e índices se leen según el planificador de consultas y la estructura física del archivo

En transacciones, el sistema general ofrece un orden de escrituras y serializabilidad y consistencia, mientras que por otro lado, dentro de una partición se ofrece unicamente consistencia lógica de la tabla.


***4. Qué pasa cuando los relojes no coinciden: ¿sincroniza con NTP, espera, desempata, lo ignora?***

PostgreSQL no tiene como resolver el desfase del tiempo entre servidores.

Esto porque no sincroniza con NTP, no espera ni desempata y como no usa un reloj de tipo lógico, la replicación de datos no se romperá. Es decir, PostgreSQL lo ignora y delega completamente al usuario o al sistema operativo. 

* Casos de uso. MongoDB. https://www.mongodb.com/es/solutions/use-cases



* Welcome to the MongoDB Docs - MongoDB Documentation - MongoDB Docs. MongoDB Documentation. https://www.mongodb.com/docs



* ¿Qué es MongoDB? (2026, June 1). IBM. https://www.ibm.com/mx-es/think/topics/mongodb



* Cabral, A. (2022, September 19). Causal guarantees are anything but casual. MongoDB. https://www.mongodb.com/company/blog/engineering/casual-guarantees-anything-casual?locale\_fallback=es



* Production Notes for Self-Managed Deployments - Database Manual - MongoDB Docs. https://www.mongodb.com/es/docs/manual/administration/production-notes/

* Gienow, M. (2026, October 21). What is CockroachDB (CRDB)? Cockroach Labs. https://www.cockroachlabs.com/blog/what-is-crdb/

* Cockroach Labs. Hybrid Logical Clock (HLC) timestamps. https://www.cockroachlabs.com/glossary/distributed-db/hybrid-logical-clock-hlc-timestamps/

* Tracy, M. (2024, April 18). Serializable, lockless, distributed: Isolation in CockroachDB. Cockroach Labs. https://www.cockroachlabs.com/blog/serializable-lockless-distributed-isolation-cockroachdb/ 

* Entin, A. (2025, April 22). Clock Management in CockroachDB: Good Timekeeping is Key. Cockroach Labs. https://www.cockroachlabs.com/blog/clock-management-cockroachdb/

* PostgreSQL: about. The PostgreSQL Global Development Group. https://www.postgresql.org/about/

* Clock-skew management in logical replication. (2024, September 20). PostgreSQL Mailing List Archives. https://www.postgresql.org/message-id/CABdArM4%3D152B9PoyF4kggQ4LniYtjBCdUjL%3DqBwD-jcogP2BPQ%40mail.gmail.com 