T04 · ¿Qué reloj usan las tecnologías que conoces?
Asignada en la S10 (24-oct) · entrega el sábado 7 de noviembre
Alumno: Fernández Herrera Mauricio - sistemas distribuidos - Grupo 02

Introducción
En los sistemas distribuidos, ordenar los eventos es un problema importante porque los diferentes procesos y servidores no necesariamente tienen el mismo reloj. 
Siempre pueden existir diferencias entre ellos, por lo que una marca de tiempo no siempre es suficiente para determinar qué evento ocurrió primero.
En esta actividad se analizan tres tecnologías que permiten observar diferentes formas de manejar el orden: PostgreSQL, MongoDB y Apache Kafka. Aunque las tres trabajan con datos, cada una tiene necesidades diferentes y, por lo tanto, utiliza mecanismos distintos para ordenar operaciones o mensajes.

1.- PostgreSQL
¿Qué es y para qué se usa?
PostgreSQL es un sistema gestor de bases de datos relacional y de código abierto. Permite almacenar, consultar y modificar información mediante SQL, además de ofrecer transacciones para mantener la consistencia de los datos.
Lo he utilizado en prácticas de bases de datos, por ejemplo, al trabajar con tablas, índices, espacios de tablas y consultas de rendimiento.
¿Qué tiempo utiliza para ordenar?
PostgreSQL no depende de un único reloj para ordenar todas sus operaciones. Este usa mecanismos de control de concurrencia y transacciones para determinar qué datos puede observar cada operación y cómo se aplican los cambios.
Por ejemplo, mediante MVCC, o Multi-Version Concurrency Control, PostgreSQL mantiene diferentes versiones de los datos para que las transacciones puedan trabajar con una vista consistente de la información.
También existen las secuencias, siendo que estas permiten generar valores numéricos únicos de forma segura cuando varias sesiones solicitan números al mismo tiempo. Sin embargo, estos números no son relojes lógicos de Lamport ni garantizan el orden de confirmación de las transacciones.
Por otra parte, las columnas de tipo timestamp permiten registrar fechas y horas físicas, pero eso no significa que PostgreSQL las utilice como mecanismo universal para ordenar las operaciones concurrentes.
¿Qué orden garantiza y hasta dónde?
Las garantías dependen del mecanismo utilizado y del nivel de aislamiento de las transacciones. Por ejemplo, con el nivel Read Committed, que es el predeterminado, cada consulta observa los datos confirmados antes de que comience esa consulta. Dos consultas consecutivas de una misma transacción pueden observar estados diferentes si otras transacciones confirman cambios entre ambas.
En cambio, el nivel Serializable busca que las transacciones concurrentes produzcan un resultado equivalente al de alguna ejecución serial. Si no es posible garantizarlo, una transacción puede abortar y necesitar ejecutarse nuevamente.
Por lo tanto, no se debe interpretar que un identificador numérico o una fecha proporcionan por sí solos un orden global de todas las transacciones.
¿Qué pasa cuando los relojes no coinciden?
La corrección de las transacciones no depende de que todos los servidores tengan exactamente la misma hora, sino que es PostgreSQL el mayor responsable al utilizar sus mecanismos de concurrencia, aislamiento y control de transacciones para mantener las garantías correspondientes.
NTP puede utilizarse para sincronizar los relojes físicos del sistema operativo, pero esa sincronización no sustituye al control de concurrencia de la base de datos.
Si se utiliza una secuencia, los números se asignan de manera segura entre sesiones concurrentes, pero pueden quedar huecos cuando una transacción se cancela. Por eso, tampoco debe asumirse que los identificadores consecutivos representan un registro perfecto de todos los acontecimientos.

2.- MongoDB
¿Qué es y para qué se usa?
MongoDB es una base de datos NoSQL orientada a documentos. En lugar de almacenar la información exclusivamente en tablas y filas, utiliza documentos BSON organizados en colecciones.
Es útil cuando se necesita almacenar información con estructuras flexibles. También permite distribuir los datos y mantener réplicas para mejorar la disponibilidad y la tolerancia a fallos.
En una práctica de Sistemas Distribuidos trabajé con un entorno de MongoDB que incluía conjuntos de réplicas y fragmentación o sharding.
¿Qué tiempo utiliza para ordenar?
MongoDB utiliza distintos mecanismos según la operación que se quiera ordenar.
En los conjuntos de réplicas, las operaciones se registran en el oplog, que permite a los miembros secundarios reproducir los cambios realizados en el miembro primario. Las entradas incluyen un OpTime, compuesto por una marca de tiempo y un término de elección, que ayuda a identificar y comparar operaciones dentro del proceso de replicación.
MongoDB también maneja tiempos lógicos, como operationTime y clusterTime, que permiten seguir el progreso de las operaciones y mantener relaciones de orden causal entre sesiones.
Estos mecanismos no deben confundirse con un reloj físico perfectamente sincronizado. Una marca de tiempo interna puede formar parte de un mecanismo de ordenamiento sin representar directamente una hora de pared.
¿Qué orden garantiza y hasta dónde?
Dentro de un conjunto de réplicas, los miembros secundarios aplican las escrituras siguiendo el orden establecido por el primario. Sin embargo, una lectura dirigida a un secundario puede devolver información atrasada si todavía no ha aplicado las últimas operaciones.
MongoDB también ofrece consistencia causal mediante sesiones configuradas adecuadamente. Para obtener las garantías causales documentadas, se utilizan operaciones con writeConcern: "majority" y lecturas con readConcern: "majority" dentro de una sesión causalmente consistente.
Esto permite mantener relaciones importantes, como leer una escritura propia o respetar el orden entre operaciones relacionadas.
Sin embargo, no significa que todas las operaciones independientes de todo el sistema tengan necesariamente un único orden global. En un clúster fragmentado, además, intervienen varios conjuntos de réplicas.
¿Qué pasa cuando los relojes no coinciden?
MongoDB no necesita que todos los relojes físicos tengan exactamente la misma hora para mantener el orden de replicación y las garantías causales que ofrece.
Los mecanismos internos de replicación y los tiempos lógicos ayudan a identificar el progreso de las operaciones y a conservar las relaciones de orden necesarias.
Si se lee desde un secundario, la información puede estar atrasada debido a la replicación asíncrona. Si se necesitan garantías causales, es importante configurar correctamente las sesiones y los niveles de confirmación y lectura.
NTP puede ayudar a sincronizar los relojes físicos de los servidores, pero no reemplaza los mecanismos internos que MongoDB utiliza para ordenar las operaciones.

3.- Apache Kafka
¿Qué es y para qué se usa?
Apache Kafka es una plataforma distribuida de eventos que permite publicar, almacenar y consumir mensajes. Se utiliza, por ejemplo, para transmitir eventos entre servicios, procesar flujos de información y desacoplar componentes de una aplicación.
En lugar de que todos los servicios se comuniquen directamente entre sí, pueden publicar eventos en Kafka para que otros componentes los consuman cuando corresponda.
¿Qué tiempo utiliza para ordenar?
Kafka utiliza principalmente un número de secuencia llamado offset para identificar la posición de cada registro dentro de una partición.
Un tópico se divide en una o varias particiones. Cada partición funciona como un registro ordenado de eventos, y los mensajes almacenados reciben posiciones identificadas mediante offsets.
Por lo tanto, para conocer el orden de los mensajes dentro de una partición no es necesario comparar las horas físicas de los servidores. El offset permite identificar la posición de cada mensaje en ese registro.
Kafka también permite utilizar marcas de tiempo en los registros. Dependiendo de la configuración, estas pueden representar el momento de creación del mensaje o el momento en que el intermediario lo añade al registro. Sin embargo, las marcas de tiempo no sustituyen al orden definido por los offsets.

¿Qué orden garantiza y hasta dónde?
Kafka garantiza el orden de lectura de los registros dentro de una misma partición. Los consumidores leen los mensajes siguiendo el orden en que aparecen en ella.
Si varios mensajes utilizan la misma clave y el productor aplica el particionamiento habitual por clave, esos mensajes se asignan a la misma partición y pueden conservar su orden dentro de ella.
Sin embargo, Kafka no garantiza automáticamente un orden total entre todas las particiones de un tópico. Si dos mensajes se almacenan en particiones diferentes, sus offsets pertenecen a registros distintos y no se pueden comparar como si fueran un único contador global.
Si una aplicación necesita un orden total para todos los mensajes de un tópico, puede utilizar una sola partición, aunque esto limita las posibilidades de paralelismo de los consumidores.
¿Qué pasa cuando los relojes no coinciden?
Las diferencias entre los relojes físicos no alteran por sí solas el orden establecido por los offsets dentro de una partición.
Por ejemplo, dos productores pueden tener relojes desincronizados y publicar eventos en Kafka. Aunque las marcas de tiempo de esos eventos indiquen horas diferentes o incluso parezcan contradictorias, los registros almacenados en una misma partición siguen teniendo un orden definido.
Kafka no necesita corregir todos los relojes mediante NTP para garantizar ese orden. La sincronización física sigue siendo útil para tareas como el diagnóstico, los registros de actividad y el análisis temporal de eventos, pero no equivale a una garantía de orden global entre particiones.


5.- Fuentes
1.	Apache Software Foundation. (s. f.). Apache Kafka documentation. https://kafka.apache.org/documentation/}
2.	MongoDB, Inc. (s. f.). Read isolation, consistency, and recency. MongoDB Manual. https://www.mongodb.com/docs/manual/core/read-isolation-consistency-recency/
3.	PostgreSQL Global Development Group. (s. f.). Transaction isolation. PostgreSQL documentation. https://www.postgresql.org/docs/current/transaction-iso.html
4.	PostgreSQL Global Development Group. (s. f.). Sequence manipulation functions. PostgreSQL documentation. https://www.postgresql.org/docs/current/functions-sequence.html
