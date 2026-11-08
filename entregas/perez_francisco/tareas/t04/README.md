# T04 · ¿Qué reloj usan las tecnologías que conoces?

## 1. PostgreSQL

**1. ¿Qué es y para qué se usa?**

PostgreSQL es un sistema de bases de datos que sirve para guardar, organizar y consultar información. Se utiliza en aplicaciones y sistemas que necesitan almacenar datos de manera segura, por ejemplo, para administrar usuarios, inventarios, pagos o movimientos bancarios.

**2. ¿Qué tiempo usa para ordenar?**

PostgreSQL utiliza un registro llamado WAL, donde se van guardando los cambios realizados en la base de datos. Cada registro tiene una posición llamada LSN, que funciona como un número de secuencia y permite conocer el orden en el que se registraron los cambios.

Para este orden no necesita relojes lógicos como Lamport o vectoriales, ni relojes híbridos, aunque también utiliza el reloj físico para funciones como `now()` y puede guardar la hora de confirmación de las transacciones si se activa.

**3. ¿Qué orden garantiza y hasta dónde?**

El WAL mantiene el orden de sus registros dentro de una misma instancia de PostgreSQL, lo que permite recuperar información cuando ocurre alguna falla y mantener actualizadas las réplicas.

Los registros de diferentes transacciones pueden aparecer intercalados, pero el orden en que se confirman queda establecido por la posición de sus registros de commit. Esto no significa que exista un mismo orden para todas las bases de datos independientes que estén en diferentes servidores.

**4. ¿Qué pasa cuando los relojes no coinciden?**

Aunque los servidores tengan horas diferentes, PostgreSQL puede seguir el orden de los registros del WAL porque utiliza las posiciones LSN en lugar de comparar las horas. No necesita esperar ni desempatar mediante NTP para realizar ese ordenamiento, aunque los servidores pueden sincronizar sus relojes con NTP para otras funciones que sí dependen de la hora.

## 2. Amazon DynamoDB

**1. ¿Qué es y para qué se usa?**

Amazon DynamoDB es una base de datos NoSQL de AWS que permite guardar y consultar grandes cantidades de información sin tener que administrar directamente los servidores. Se utiliza en aplicaciones que necesitan trabajar con muchos datos de forma rápida, por ejemplo, para guardar usuarios, pedidos o movimientos de una aplicación.

**2. ¿Qué tiempo usa para ordenar?**

DynamoDB utiliza diferentes mecanismos dependiendo de la operación. En DynamoDB Streams, que sirve para registrar los cambios de los datos, utiliza números de secuencia asignados por el sistema, mientras que en las tablas globales con consistencia eventual utiliza marcas de tiempo físicas para resolver conflictos entre regiones.

Un detalle interesante es que el sistema Dynamo original utilizaba relojes vectoriales, pero eso no significa que DynamoDB utilice ese mismo mecanismo para ordenar sus cambios.

**3. ¿Qué orden garantiza y hasta dónde?**

En DynamoDB Streams se garantiza el orden de los cambios de un mismo elemento identificado por su llave primaria, pero no existe un orden general para toda la tabla.

Por ejemplo, si modificamos varias veces la información de un usuario, sus cambios aparecen en el orden en que se realizaron, aunque no podemos asegurar un orden general entre los cambios de todos los usuarios.

DynamoDB también tiene tablas globales con consistencia fuerte entre regiones, pero funcionan de manera diferente a las de consistencia eventual.

**4. ¿Qué pasa cuando los relojes no coinciden?**

En DynamoDB Streams las diferencias entre relojes no afectan directamente el orden de los cambios, porque se utilizan números de secuencia. En las tablas globales con consistencia eventual, cuando dos regiones modifican el mismo elemento, DynamoDB compara las marcas de tiempo y conserva la actualización que considera más reciente, un mecanismo llamado *last writer wins*.

Por eso, una diferencia entre los relojes puede influir en cuál actualización se conserva. No espera a que todos los relojes coincidan ni utiliza NTP como mecanismo para desempatar estos conflictos.

En cambio, las tablas globales con consistencia fuerte coordinan las escrituras entre regiones y no utilizan ese mismo mecanismo de resolución de conflictos.

## 3. Apache Kafka

**1. ¿Qué es y para qué se usa?**

Apache Kafka es una plataforma que permite enviar, guardar y procesar grandes cantidades de mensajes o eventos. Se utiliza cuando diferentes sistemas necesitan compartir información constantemente, por ejemplo, para registrar compras, pagos o movimientos que se realizan dentro de una aplicación.

**2. ¿Qué tiempo usa para ordenar?**

Kafka utiliza números llamados offsets, que el sistema asigna a los mensajes conforme se guardan en una partición. Cada mensaje tiene su propia posición, lo que permite saber cuál quedó registrado antes y cuál después, sin tener que utilizar relojes de Lamport, vectoriales o híbridos.

Los mensajes también tienen una marca de tiempo física, que puede venir del productor o del servidor de Kafka. Esta hora sirve para otras funciones, como la retención de mensajes y el procesamiento por ventanas de tiempo, pero no es la que determina su orden dentro de la partición.

**3. ¿Qué orden garantiza y hasta dónde?**

Kafka mantiene el orden de los mensajes dentro de una misma partición, pero no entre diferentes particiones ni en todo el sistema.

Por ejemplo, si queremos mantener ordenados los movimientos de una cuenta, podemos utilizar la misma llave para que lleguen a la misma partición, siempre que no cambie la distribución de particiones. También es importante configurar correctamente los reintentos del productor, ya que sin idempotencia podrían alterar el orden de envío cuando ocurre alguna falla.

**4. ¿Qué pasa cuando los relojes no coinciden?**

Si los servidores tienen horarios diferentes, Kafka sigue manteniendo el orden de los mensajes mediante los offsets asignados a cada partición. No tiene que esperar a que los relojes se sincronicen ni comparar las horas para decidir cuál mensaje va primero.

Aunque se puede utilizar NTP para mantener las horas de los servidores sincronizadas, el orden de los mensajes no depende de eso, sino de la posición en la que fueron guardados.

## Referencias

1. PostgreSQL. (s. f.). *WAL Internals*. PostgreSQL Documentation. Recuperado el 8 de noviembre de 2026, de https://www.postgresql.org/docs/17/wal-internals.html

2. PostgreSQL. (s. f.). *System Information Functions and Operators*. PostgreSQL Documentation. Recuperado el 8 de noviembre de 2026, de https://www.postgresql.org/docs/current/functions-info.html

3. Amazon Web Services. (s. f.). *Change data capture for DynamoDB Streams*. Amazon DynamoDB Developer Guide. Recuperado el 8 de noviembre de 2026, de https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.html

4. Amazon Web Services. (s. f.). *How DynamoDB global tables work*. Amazon DynamoDB Developer Guide. Recuperado el 8 de noviembre de 2026, de https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/V2globaltables_HowItWorks.html

5. Apache Software Foundation. (s. f.). *Apache Kafka – Design*. Apache Kafka Documentation. Recuperado el 8 de noviembre de 2026, de https://kafka.apache.org/41/design/design/

6. Apache Software Foundation. (s. f.). *Apache Kafka – Producer Configs*. Apache Kafka Documentation. Recuperado el 8 de noviembre de 2026, de https://kafka.apache.org/41/configuration/producer-configs/
