# T04 — ¿Qué reloj usan las tecnologías que conoces?

## 1. PostgreSQL

**¿Qué es y para qué se usa?**

PostgreSQL es un sistema de gestión de bases de datos relacionales. Permite guardar y consultar información mediante SQL. Lo utilizamos en Mexi Banco para almacenar cuentas, movimientos y transferencias.

**¿Qué tiempo utiliza para ordenar?**

Para registrar los cambios, PostgreSQL utiliza un mecanismo llamado WAL (*Write-Ahead Log*). Cada posición dentro de este registro tiene un número llamado LSN (*Log Sequence Number*), que aumenta conforme se escriben nuevos registros.

Por lo tanto, el WAL utiliza una secuencia para ordenar sus registros, no la hora de los servidores. PostgreSQL también utiliza transacciones y mecanismos de control de concurrencia para manejar las operaciones simultáneas.

**¿Qué orden garantiza?**

El WAL conserva el orden de sus registros dentro de una misma instancia de PostgreSQL, lo que permite recuperar cambios y reproducirlos en una réplica.

Sin embargo, ese orden no significa que todas las consultas se ejecuten una después de otra. Las garantías entre transacciones dependen del nivel de aislamiento. Por ejemplo, SERIALIZABLE hace que las transacciones confirmadas tengan un resultado equivalente a ejecutarse en algún orden secuencial.

**¿Qué pasa cuando los relojes no coinciden?**

PostgreSQL no necesita que los relojes de sus servidores marquen exactamente la misma hora para mantener el orden de los registros WAL, ya que utiliza posiciones LSN para identificar la secuencia de los cambios.

Por ejemplo, si una réplica tiene su reloj adelantado cinco minutos, esto no cambia el orden en que debe reproducir los registros recibidos desde el servidor principal. Sin embargo, las fechas registradas en los logs podrían no coincidir y dificultar la identificación del momento real en que ocurrió una operación.

Por ello, aunque el orden del WAL no depende directamente de los relojes, mantenerlos sincronizados facilita la supervisión y el análisis de errores.

## 2. RabbitMQ

**¿Qué es y para qué se usa?**

RabbitMQ es un sistema de mensajería que permite enviar información entre aplicaciones mediante colas. Lo utilizamos en Mexi Banco para que Transferencia enviara eventos a Notificación sin depender de una llamada HTTP directa.

**¿Qué tiempo utiliza para ordenar?**

RabbitMQ utiliza principalmente el orden en el que los mensajes ingresan a una cola. Este comportamiento se conoce como FIFO (*First In, First Out*), es decir, el primero en entrar es el primero en salir.

No necesita comparar la hora de los servidores para decidir qué mensaje se entrega primero.

**¿Qué orden garantiza?**

El orden FIFO se aplica dentro de una cola. Cuando los mensajes se publican desde un mismo canal, RabbitMQ conserva su orden de publicación al ingresarlos a la cola.

Sin embargo, esto no significa que todos los mensajes del sistema tengan un orden global. Además, si hay varios consumidores, prioridades o reintentos, el orden en el que terminan de procesarse puede cambiar.

**¿Qué pasa cuando los relojes no coinciden?**

RabbitMQ no utiliza las marcas de tiempo de los servidores para establecer el orden de los mensajes dentro de una cola. En condiciones normales, utiliza el orden de entrada FIFO.

Por ejemplo, si el productor A tiene su reloj adelantado y el productor B tiene su reloj atrasado, RabbitMQ no ordena sus mensajes comparando esas horas, sino según el orden en que ingresan a la cola.

Sin embargo, las diferencias entre relojes pueden dificultar la interpretación de registros y marcas de tiempo. Además, cuando existen varios consumidores o mensajes que deben reintentarse, el orden en que terminan de procesarse puede ser diferente al de entrada.


## 3. Apache Kafka

**¿Qué es y para qué se usa?**

Apache Kafka es una plataforma que permite publicar, almacenar y procesar eventos. Se utiliza en aplicaciones que necesitan manejar grandes cantidades de mensajes, como registros de actividad, movimientos bancarios o eventos de sistemas.

**¿Qué tiempo utiliza para ordenar?**

Kafka utiliza números de secuencia llamados *offsets*. Cada mensaje almacenado dentro de una partición recibe una posición que indica dónde se encuentra.

De esta manera, Kafka puede ordenar los mensajes sin depender de la hora que marca el reloj del servidor.

**¿Qué orden garantiza?**

Kafka garantiza el orden de los mensajes dentro de una misma partición, pero no entre particiones diferentes.

Por ejemplo, si los mensajes A, B y C se guardan en la misma partición con offsets consecutivos, el consumidor puede leerlos en ese orden.

En cambio, si los mensajes están repartidos entre varias particiones, Kafka no garantiza un orden único para todos ellos.

**¿Qué pasa cuando los relojes no coinciden?**

Kafka mantiene el orden de los mensajes mediante sus offsets dentro de cada partición, independientemente de las diferencias entre los relojes.

Sin embargo, también maneja dos tipos de marcas de tiempo: CreateTime, que corresponde al tiempo asignado por el productor, y LogAppendTime, que corresponde al momento en que el broker almacena el mensaje.

Si el reloj del productor está adelantado o atrasado, CreateTime puede representar una hora incorrecta. Cuando se utiliza LogAppendTime, Kafka asigna la marca de tiempo utilizando el reloj del broker.

Por ejemplo, si un mensaje tiene CreateTime de las 10:00, llega al broker a las 10:02 y un consumidor lo procesa hasta las 10:07, su posición dentro de la partición no cambia. El retraso del consumidor tampoco modifica la marca de tiempo almacenada.

Por ello, aunque Kafka garantiza el orden mediante offsets, las diferencias entre relojes pueden afectar los análisis que utilizan marcas de tiempo para agrupar eventos o determinar cuándo ocurrieron.

## Conclusión

Las tres tecnologías utilizan mecanismos diferentes para establecer un orden sin depender únicamente de la hora.

PostgreSQL utiliza registros WAL con posiciones LSN y mecanismos de control de transacciones. RabbitMQ organiza sus mensajes mediante colas FIFO, mientras que Kafka asigna offsets dentro de cada partición.


## Fuentes consultadas

1. PostgreSQL Global Development Group. *WAL Internals*. https://www.postgresql.org/docs/current/wal-internals.html
2. PostgreSQL Global Development Group. *Transaction Isolation*. https://www.postgresql.org/docs/current/transaction-iso.html
3. RabbitMQ. *Queues*. https://www.rabbitmq.com/docs/queues
4. Apache Software Foundation. *Apache Kafka — Design*. https://kafka.apache.org/41/design/design/
