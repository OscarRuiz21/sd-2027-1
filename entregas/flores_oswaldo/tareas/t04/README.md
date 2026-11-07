MongoDB

Qué es y para qué se usa.

MongoDB es una base de datos de documentos diseñada para ayudar a los desarrolladores a crear aplicaciones más rápido. Guarda los datos en documentos flexibles parecidos a JSON, llamados BSON, en lugar de usar tablas con columnas fijas como las bases de datos relacionales. Esto permite manejar datos que no tienen siempre la misma estructura, cambiar el modelo de datos sin detener la aplicación y escalar horizontalmente cuando la cantidad de información crece. Se usa en aplicaciones como perfiles de usuario, catálogos de productos, contenido web, sistemas de mensajería y plataformas que necesitan almacenar información variable.

Qué tiempo usa para ordenar: ¿reloj físico (de pared o monotónico), reloj lógico (estilo Lamport o vectorial), un híbrido, o un número de secuencia que asigna alguien?

MongoDB usa tiempo lógico para ordenar operaciones. Cada operación puede recibir un cluster time, que es un tiempo lógico utilizado para ordenar las operaciones dentro del clúster. Este mecanismo permite comparar eventos aunque los relojes físicos de los servidores no estén perfectamente sincronizados.

Qué orden te garantiza y hasta dónde: ¿dentro de una partición, de una llave, de un proceso, en todo el sistema?

Con sesiones causalmente consistentes, MongoDB ejecuta las operaciones causales en un orden que respeta sus relaciones causales. Es decir, si una operación depende de otra anterior, MongoDB garantiza que la segunda vea el efecto de la primera. Esto funciona dentro de una misma sesión; no garantiza un orden total entre todas las sesiones o todos los clientes del sistema.

Qué pasa cuando los relojes no coinciden: ¿sincroniza con NTP, espera, desempata, lo ignora?

La sesión del cliente guarda el operation time y el cluster time recibidos de las operaciones anteriores. Los drivers y mongosh permiten avanzar esos valores para que una sesión sea consistente con las operaciones de otra sesión. Así, MongoDB no depende de que todos los servidores tengan exactamente la misma hora física, porque utiliza el tiempo lógico para mantener el orden causal.

Amazon DynamoDB

Qué es y para qué se usa.

DynamoDB es un servicio de base de datos NoSQL totalmente administrado por AWS. Almacena los datos como ítems identificados por una llave, en lugar de usar tablas relacionales con esquemas rígidos. Está diseñado para aplicaciones que necesitan respuestas rápidas, rendimiento predecible y escalabilidad automática. Se utiliza comúnmente para perfiles de usuario, sesiones, carritos de compra, contadores, catálogos y sistemas que reciben muchas solicitudes al mismo tiempo.

Qué tiempo usa para ordenar: ¿reloj físico (de pared o monotónico), reloj lógico (estilo Lamport o vectorial), un híbrido, o un número de secuencia que asigna alguien?

DynamoDB no usa un reloj de Lamport como mecanismo principal para ordenar escrituras concurrentes. En su lugar, utiliza números de versión y escrituras condicionales. Una escritura solo se acepta si el ítem cumple la condición esperada, por ejemplo, si la versión que la aplicación leyó sigue siendo la actual.

Qué orden te garantiza y hasta dónde: ¿dentro de una partición, de una llave, de un proceso, en todo el sistema?

DynamoDB Streams asigna a cada registro un número de secuencia que refleja el orden en que fue publicado en el stream. Los registros se organizan en grupos llamados shards. El orden garantizado es dentro de cada shard, normalmente asociado a un ítem o llave primaria; no garantiza un orden global entre todos los ítems de la tabla.

Qué pasa cuando los relojes no coinciden: ¿sincroniza con NTP, espera, desempata, lo ignora?

DynamoDB no compara relojes físicos para decidir qué escritura gana. Utiliza optimistic locking, un mecanismo que detecta conflictos al momento de escribir. Cada ítem puede tener un atributo de versión que se incrementa con cada actualización; si la versión cambió antes de que otro proceso intentara escribir, la condición falla y la escritura no se aplica.

Apache Kafka

Qué es y para qué se usa.

Kafka es una plataforma de event streaming. Permite publicar y suscribirse a flujos de eventos, almacenarlos de forma duradera y procesarlos en tiempo real o después. Se usa para comunicar servicios entre sí, un servicio puede publicar un evento, como pago realizado, y otros servicios pueden consumirlo para actualizar inventario, enviar notificaciones o realizar análisis.

Qué tiempo usa para ordenar: ¿reloj físico (de pared o monotónico), reloj lógico (estilo Lamport o vectorial), un híbrido, o un número de secuencia que asigna alguien?

Kafka no ordena los eventos mediante un reloj lógico de Lamport. El orden principal viene de la posición del evento dentro de una partición. Cuando se publica un evento, este se agrega a una de las particiones de ese tópico.

Qué orden te garantiza y hasta dónde: ¿dentro de una partición, de una llave, de un proceso, en todo el sistema?

Kafka garantiza que los consumidores de una partición lean los eventos en exactamente el mismo orden en que fueron escritos. Los eventos con la misma llave se escriben en la misma partición, por lo que conservan el orden entre sí. Sin embargo, Kafka no garantiza orden entre particiones distintas de un mismo tópico.

Qué pasa cuando los relojes no coinciden: ¿sincroniza con NTP, espera, desempata, lo ignora?

Para el orden dentro de una partición, Kafka no depende de que los relojes de los productores estén sincronizados, porque utiliza la posición del evento en la partición. El timestamp es parte del evento, pero la garantía de orden se basa en el orden de escritura de la partición, no en la hora registrada en el mensaje.

Referencias 

MongoDB, “What is MongoDB? An Overview of the Database,” MongoDB Documentation. [En línea]. Disponible en: https://www.mongodb.com/docs/manual/introduction/github

MongoDB, “Use Database Commands,” MongoDB Documentation. [En línea]. Disponible en: https://www.mongodb.com/docs/manual/tutorial/use-database-commands/mongodb

MongoDB, “Read Isolation, Consistency, and Recency,” MongoDB Documentation. [En línea]. Disponible en: https://www.mongodb.com/docs/manual/core/read-isolation-consistency-recency/kafka.apache

Amazon Web Services, “¿Qué es Amazon DynamoDB?,” Amazon DynamoDB Developer Guide. [En línea]. Disponible en: https://docs.aws.amazon.com/es_es/amazondynamodb/latest/developerguide/Introduction.htmlkafka.apache

Amazon Web Services, “Working with items and attributes in DynamoDB,” Amazon DynamoDB Developer Guide. [En línea]. Disponible en: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/WorkingWithItems.htmlkafka.apache

Amazon Web Services, “Change data capture for DynamoDB Streams,” Amazon DynamoDB Developer Guide. [En línea]. Disponible en: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.htmlmongodb

Amazon Web Services, “Optimistic locking with version number,” Amazon DynamoDB Developer Guide. [En línea]. Disponible en: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/BestPractices_OptimisticLocking.htmlkafka.apache

Apache Software Foundation, “Introduction,” Apache Kafka Documentation. [En línea]. Disponible en: https://kafka.apache.org/intro
