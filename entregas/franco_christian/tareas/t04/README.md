# T04. ¿Qué reloj usan las tecnologías que conozco?

## Introducción

En los sistemas distribuidos es importante mantener un orden de los eventos, especialmente cuando varios procesos trabajan al mismo tiempo. Sin embargo, los relojes físicos de diferentes equipos no siempre coinciden. Por eso, algunas tecnologías utilizan mecanismos propios para organizar sus operaciones sin depender completamente de la hora del sistema.

A continuación, se presentan tres tecnologías y la forma en que manejan el orden de sus operaciones.

## 1. PostgreSQL

**¿Qué es?**

PostgreSQL es un sistema de gestión de bases de datos relacional que permite almacenar, consultar y administrar información. Se utiliza principalmente en aplicaciones que necesitan mantener sus datos organizados y consistentes.

**¿Qué tiempo utiliza?**

Utiliza identificadores de transacción (`XID`) que se asignan mediante un contador secuencial. Estos permiten identificar las transacciones y forman parte del mecanismo MVCC. Sin embargo, no representan directamente el orden en que las transacciones terminan.

**¿Qué orden garantiza?**

Permite controlar la visibilidad de los cambios entre transacciones y mantener la consistencia de los datos. No garantiza un orden global de finalización basado únicamente en los identificadores.

**¿Qué pasa si los relojes no coinciden?**

La hora del sistema puede utilizarse para registrar fechas y tiempos, pero la consistencia de las transacciones no depende de que todos los relojes estén sincronizados. Para controlar las operaciones utiliza sus propios mecanismos internos.

## 2. Redis

**¿Qué es?**

Redis es una base de datos en memoria que se utiliza principalmente como caché y almacenamiento temporal, permitiendo consultar información rápidamente.

**¿Qué tiempo utiliza?**

En una instancia principal, las operaciones se procesan secuencialmente. Además, utiliza un desplazamiento de replicación (*replication offset*) que funciona como una referencia lógica para identificar el avance de los cambios enviados a las réplicas.

**¿Qué orden garantiza?**

El orden de las operaciones se mantiene en la instancia principal. Durante la replicación, los cambios se envían siguiendo el orden del flujo generado por dicha instancia. Sin embargo, esto no garantiza un orden global entre diferentes instancias independientes.

**¿Qué pasa si los relojes no coinciden?**

Redis no depende de NTP para ordenar sus operaciones. Las réplicas siguen los cambios enviados por la instancia principal, por lo que no necesitan tener sus relojes físicos sincronizados para mantener el orden de actualización.

## 3. RabbitMQ

**¿Qué es?**

RabbitMQ es un sistema de mensajería que permite comunicar diferentes aplicaciones o servicios mediante el envío y recepción de mensajes. Se utiliza para distribuir tareas y facilitar la comunicación entre procesos.

**¿Qué tiempo utiliza?**

Utiliza principalmente un orden basado en la llegada de los mensajes a una cola (FIFO: *First In, First Out*). No necesita utilizar relojes lógicos o físicos para establecer este orden.

**¿Qué orden garantiza?**

En una misma cola, los mensajes se almacenan siguiendo el orden en que llegan. Sin embargo, cuando existen varios productores, consumidores o reenvíos de mensajes, el orden de procesamiento puede cambiar.

**¿Qué pasa si los relojes no coinciden?**

La diferencia entre los relojes de los equipos no afecta directamente el orden de los mensajes, ya que RabbitMQ utiliza el orden de llegada a la cola. Por lo tanto, no necesita sincronizar los relojes mediante NTP para mantener su funcionamiento FIFO.

## Conclusión

Después de revisar estas tecnologías, podemos observar que no todas necesitan utilizar relojes físicos para ordenar sus operaciones. PostgreSQL utiliza identificadores y mecanismos de control de transacciones, Redis mantiene un orden de ejecución en su instancia principal y RabbitMQ utiliza el orden de llegada de los mensajes a sus colas.

Esto demuestra que el orden en los sistemas distribuidos no depende únicamente de conocer la hora exacta, sino de utilizar mecanismos que permitan coordinar correctamente las operaciones.

## Fuentes de consulta

PostgreSQL Global Development Group. (s. f.). Concurrency control. PostgreSQL Documentation. https://www.postgresql.org/docs/current/mvcc.html

PostgreSQL Global Development Group. (s. f.). Transactions and identifiers. PostgreSQL Documentation. https://www.postgresql.org/docs/current/transaction-id.html

Redis Ltd. (s. f.). Replication. Redis Documentation. https://redis.io/docs/latest/manual/replication/

VMware, Inc. (s. f.). Broker semantics. RabbitMQ Documentation. https://www.rabbitmq.com/docs/semantics
