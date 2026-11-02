\# T04 · ¿Qué reloj usan las tecnologías que conoces?



\## Apache Kafka



1\. \*\*¿Qué es y para qué se usa?\*\*

&#x20;  Kafka es una plataforma que sirve para mover y guardar mensajes (eventos) entre aplicaciones. Las aplicaciones, los productores, escriben mensajes en un tópico, y otras, como los consumidores, los leen. Los mensajes se guardan en un registro (log) por un tiempo configurable, así que se pueden volver a leer. Se usa mucho para comunicar microservicios, juntar logs y métricas, y procesar datos en tiempo real (Apache Software Foundation, s. f.).



2\. \*\*¿Qué tiempo usa para ordenar?\*\*

&#x20;  Usa un número de secuencia que asigna alguien, no el reloj. Cada tópico se divide en particiones, y cada mensaje que llega a una partición recibe un número llamado offset (0, 1, 2...). Ese número lo asigna el broker líder de la partición, y con él se decide qué mensaje va antes. Kafka también guarda un timestamp en cada mensaje, que puede ser la hora en que lo creó el productor (CreateTime) o la hora en que el broker lo guardó (LogAppendTime), según cómo se configure el tópico (Apache Kafka, s. f.). Pero ese timestamp es más bien información extra, no es lo que define el orden.



3\. \*\*¿Qué orden garantiza y hasta dónde?\*\*

&#x20;  Solo garantiza el orden dentro de una partición. Si un productor manda M1 y luego M2 a la misma partición, M1 tendrá un offset menor, y los consumidores leen en ese orden (Apache Software Foundation, s. f.). Entre particiones distintas no hay orden garantizado. Si se requiere que los mensajes de una misma llave (por ejemplo, un mismo usuario) queden en orden, se tiene que mandar a la misma partición. Y si se quisiera orden total en todo el tópico, se tendría que usar una sola partición, pero se perdería el paralelismo.



4\. \*\*¿Qué pasa cuando los relojes no coinciden?\*\*

&#x20;  Como el orden depende del offset y no de la hora, Kafka no necesita sincronizar relojes, ni esperar, ni desempatar. Los offsets son únicos dentro de la partición, así que no hay empates. Lo que sí puede pasar es que los timestamps salgan raros: si se usa CreateTime, cuenta el reloj de cada productor y los timestamps pueden no ir en aumento. Por eso existe una configuración que permite al broker rechazar mensajes cuyo timestamp se aleje demasiado de su propia hora. Con LogAppendTime se usa el reloj del broker y los timestamps sí van creciendo (Apache Software Foundation, s. f.).



\## MongoDB



1\. \*\*¿Qué es y para qué se usa?\*\*

&#x20;  MongoDB es una base de datos NoSQL orientada a documentos. En lugar de tablas con filas, guarda documentos parecidos a JSON, que pueden tener estructuras distintas entre sí. Permite replicación (replica sets, donde un nodo primario recibe las escrituras y los secundarios las copian) y sharding para repartir los datos en varios servidores. Se usa en aplicaciones web y móviles, catálogos de productos y datos que cambian de forma seguida (MongoDB, Inc., s. f.).



2\. \*\*¿Qué tiempo usa para ordenar?\*\*

&#x20;  Usa un reloj lógico híbrido. Por dentro maneja un "cluster time", que combina una parte de tiempo físico con un contador lógico, con una idea parecida a la de Lamport (Tyulenev et al., 2019). Ese valor aumenta cuando se escribe una operación en el oplog, que es el registro de cambios del primario. Cada respuesta del servidor incluye un operationTime y un clusterTime, y el cliente los guarda y los manda en las siguientes peticiones (MongoDB, Inc., s. f.).



3\. \*\*¿Qué orden garantiza y hasta dónde?\*\*

&#x20;  Dentro de un replica set, el oplog del primario define un orden para las escrituras. Para los clientes, la garantía más importante es la consistencia causal, que funciona dentro de una sesión: puedo leer lo que yo mismo escribí, las lecturas no "retroceden" y una escritura que hago después de leer queda después de esa lectura (Tyulenev et al., 2019). Si dos clientes distintos no comparten sesión, MongoDB no promete ese orden causal entre ellos. Tampoco es lo mismo que una transacción de varios documentos, que es una garantía aparte.



4\. \*\*¿Qué pasa cuando los relojes no coinciden?\*\*

&#x20;  No depende de que los relojes físicos estén perfectamente sincronizados. Los nodos intercambian el cluster time en cada mensaje y se quedan con el mayor, parecido a como funciona Lamport (Tyulenev et al., 2019). Si leo desde un secundario que va atrasado, el servidor espera hasta haber aplicado las operaciones hasta el tiempo que le pasé, y si tarda demasiado se corta por el tiempo máximo (maxTimeMS). Además, el cluster time va firmado para que un cliente no pueda mandar un tiempo inventado y desordenar el sistema (MongoDB, Inc., s. f.).



\## PostgreSQL



1\. \*\*¿Qué es y para qué se usa?\*\*

&#x20;  PostgreSQL es una base de datos relacional de código abierto. Guarda los datos en tablas, usa SQL y cumple con las propiedades ACID, o sea que las transacciones se hacen completas o no se hacen. Se usa en sistemas donde importa mucho la consistencia, como aplicaciones bancarias, sistemas de inventario, páginas web y análisis de datos (PostgreSQL Global Development Group, s. f.).



2\. \*\*¿Qué tiempo usa para ordenar?\*\*

&#x20;  Usa un número de secuencia, no el reloj de pared. Cada transacción recibe un identificador (xid) que va aumentando, y con MVCC (control de concurrencia multiversión) se guardan varias versiones de la misma fila, marcadas con el id de la transacción que las creó o borró (Suzuki, s. f.). Cada consulta trabaja con un "snapshot", una foto de qué transacciones ya estaban confirmadas, y con eso decide qué versión ve (PostgreSQL Global Development Group, s. f.). También existe la opción track\_commit\_timestamp, que guarda la hora de commit, pero es informativa y no es lo que ordena.



3\. \*\*¿Qué orden garantiza y hasta dónde?\*\*

&#x20;  En un solo servidor, el orden de las transacciones es el mismo para todo el sistema, porque todas comparten la misma numeración. Lo que cada transacción ve depende del nivel de aislamiento: en Read Committed (el predeterminado) cada consulta ve lo confirmado hasta ese momento, y en Serializable el resultado es como si las transacciones se hubieran ejecutado una por una (PostgreSQL Global Development Group, s. f.). Con réplicas, la garantía se complica, porque una réplica puede ir un poco atrasada respecto al servidor principal. Este punto lo consulté con apoyo de Claude, un asistente de inteligencia artificial (Anthropic, 2026).



4\. \*\*¿Qué pasa cuando los relojes no coinciden?\*\*

&#x20;  En un solo servidor el problema casi no existe, porque el orden no depende de la hora. Los relojes solo importan si activo los timestamps de commit o si uso funciones como now(). PostgreSQL por sí solo no sincroniza relojes con NTP ni desempata entre servidores por hora, así que si tengo varios servidores, la sincronización del reloj es un tema del sistema operativo o de otras herramientas.



\## Referencias



\- Anthropic. (2026). Claude (versión Sonnet 5.5) \[Modelo de lenguaje de gran tamaño]. Recuperado el 2 de noviembre del 2026, de https://claude.ai

\- Apache Kafka. (s. f.). KIP-32- Add timestamps to Kafka message. Apache Software Foundation. Recuperado el 2 de noviembre del 2026, de https://cwiki.apache.org/confluence/display/KAFKA/KIP-32+-+Add+timestamps+to+Kafka+message

\- Apache Software Foundation. (s. f.). Apache Kafka documentation. Recuperado el 2 de noviembre del 2026, de https://kafka.apache.org/documentation/

\- MongoDB, Inc. (s. f.). Use database commands. En MongoDB manual. Recuperado el 2 de noviembre del 2026, de https://mongodb.com/docs/rapid/tutorial/use-database-commands

\- PostgreSQL Global Development Group. (s. f.). Concurrency control: Introduction. En PostgreSQL documentation. Recuperado el 2 de noviembre del 2026, de https://www.postgresql.org/docs/current/mvcc-intro.html

\- Suzuki, H. (s. f.). Chapter 5: Concurrency control. En The internals of PostgreSQL. Recuperado el 2 de noviembre del 2026, de https://interdb.jp/pg/pgsql05.html

\- Tyulenev, M., Schwerin, A., Kamsky, A., Tan, R., Cabral, A., \& Mulrow, J. (2019). Implementation of cluster-wide logical clock and causal consistency in MongoDB. En Proceedings of the 2019 International Conference on Management of Data (SIGMOD '19) (pp. 636-650). ACM. Recuperado el 2 de noviembre del 2026, de https://doi.org/10.1145/3299869.3314049

