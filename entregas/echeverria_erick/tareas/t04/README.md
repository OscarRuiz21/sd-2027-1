# T04 · ¿Qué reloj usan las tecnologías que conoces?

**Alumno: Echeverria Goicochea Erick Isaac**

---

## 1. MongoDB

### 1. ¿Qué es y para qué se usa?

MongoDB es un sistema de gestión de bases de datos NoSQL que almacena la información en documentos flexibles en lugar de utilizar tablas y filas. Se utiliza principalmente para almacenar y consultar grandes cantidades de información, especialmente cuando se necesita flexibilidad en la estructura de los datos y capacidad de crecimiento mediante la distribución entre varios servidores (sharding).

### 2. ¿Qué tiempo usa para ordenar?

MongoDB utiliza principalmente un reloj lógico mediante un mecanismo llamado Logical Clock. Este genera marcas de tiempo lógicas, conocidas como clusterTime, que permiten identificar y ordenar eventos dentro del clúster. Aunque estas marcas tienen una representación de tiempo, no deben confundirse con la hora real del reloj de pared.

### 3. ¿Qué orden te garantiza y hasta dónde?

MongoDB ofrece garantías de orden causal cuando se utilizan sesiones causalmente consistentes. Esto significa que si una operación depende de otra, el sistema puede garantizar que la primera se observe antes que la segunda. Esta garantía se aplica a las operaciones relacionadas dentro de una sesión causalmente consistente, incluso cuando intervienen diferentes servidores. Sin embargo, no significa que todas las operaciones del clúster tengan un orden cronológico global.

### 4. ¿Qué pasa cuando los relojes no coinciden?

Como utiliza un reloj lógico para identificar el orden de los eventos, no depende exclusivamente de que todos los servidores tengan exactamente la misma hora física. Los nodos intercambian información sobre sus marcas de tiempo lógicas para mantener una referencia común del avance del clúster. Esto permite conservar el orden causal incluso cuando existen diferencias entre los relojes físicos.

---

## 2. Apache Kafka

### 1. ¿Qué es y para qué se usa?

Apache Kafka es una plataforma distribuida de procesamiento de eventos que permite enviar, almacenar y consumir grandes cantidades de mensajes en tiempo real. Se utiliza principalmente en sistemas que necesitan intercambiar información entre diferentes servicios, procesar eventos y mantener registros de las actividades que ocurren en una aplicación.

### 2. ¿Qué tiempo usa para ordenar?

Kafka utiliza principalmente números de secuencia llamados offsets, que son asignados a cada mensaje dentro de una partición. También maneja marcas de tiempo (timestamps), que pueden representar el momento en que el productor creó el mensaje (CreateTime) o el momento en que el broker lo recibió (LogAppendTime). Sin embargo, los timestamps no son los que determinan el orden de lectura; este se establece mediante los offsets.

### 3. ¿Qué orden te garantiza y hasta dónde?

Kafka garantiza que los mensajes se mantengan en el orden en que fueron almacenados dentro de una misma partición. Esto significa que un consumidor puede leer los mensajes siguiendo sus offsets, pero no existe una garantía de orden global entre diferentes particiones. Por ejemplo, si dos mensajes se almacenan en particiones distintas, Kafka no garantiza cuál será procesado primero.

### 4. ¿Qué pasa cuando los relojes no coinciden?

Kafka no necesita sincronizar los relojes físicos de todos los servidores para mantener el orden de los mensajes, ya que utiliza los offsets como referencia principal. Si se utiliza CreateTime, las diferencias entre los relojes de los productores pueden provocar que los timestamps no coincidan con el orden real de llegada. En cambio, con LogAppendTime, el broker asigna la marca de tiempo cuando recibe el mensaje. En ambos casos, el orden dentro de la partición sigue dependiendo de los offsets.

---

## 3. Amazon DynamoDB

### 1. ¿Qué es y para qué se usa?

Amazon DynamoDB es un sistema de gestión de bases de datos NoSQL desarrollado por Amazon Web Services (AWS). Permite almacenar y consultar información sin necesidad de administrar directamente los servidores. Se utiliza principalmente en aplicaciones que necesitan responder rápidamente a muchas solicitudes y manejar grandes cantidades de datos, como plataformas de comercio electrónico, aplicaciones móviles y sistemas distribuidos.

### 2. ¿Qué tiempo usa para ordenar?

DynamoDB utiliza principalmente números de secuencia (sequence numbers) para identificar el orden de los cambios registrados en DynamoDB Streams. Cada registro recibe un número de secuencia que permite identificar su posición dentro del flujo de eventos. Además, en las tablas globales que utilizan replicación entre regiones con consistencia eventual, DynamoDB utiliza marcas de tiempo para resolver conflictos mediante un mecanismo llamado Last Writer Wins (el último escritor gana).

### 3. ¿Qué orden te garantiza y hasta dónde?

DynamoDB garantiza que los cambios realizados sobre un mismo elemento (item) aparezcan en el mismo orden en DynamoDB Streams. Sin embargo, no garantiza un orden global entre todos los elementos de la tabla. Esto significa que si se modifican dos elementos diferentes, no necesariamente se podrá determinar cuál cambio ocurrió primero utilizando únicamente el orden de los registros del flujo.

### 4. ¿Qué pasa cuando los relojes no coinciden?

En DynamoDB Streams, el orden de los cambios de un mismo elemento se mantiene mediante los números de secuencia, por lo que no depende de que los relojes de los servidores estén perfectamente sincronizados. Sin embargo, en las tablas globales con consistencia eventual, cuando dos regiones modifican el mismo elemento al mismo tiempo, DynamoDB utiliza las marcas de tiempo de las escrituras para resolver el conflicto. En este caso, prevalece la escritura que tenga la marca de tiempo más reciente.

---

## Algoritmo de Relojes Lógicos de Lamport

### Estructura de la carpeta
* `Main.java`: Implementación en Java de la simulación de 3 servidores.
* `salida.txt`: Captura de la consola al ejecutar la simulación.

### Explicación de la Causalidad

La relación de causalidad de Lamport ($a \to b$) impone la condición de que si el evento $a$ causa el evento $b$, el valor del reloj lógico en $a$ debe ser estrictamente menor que el valor en $b$ ($C(a) < C(b)$).

Esta propiedad se cumple en la salida debido a dos reglas aplicadas con `AtomicLong`:

1. **Orden en el mismo proceso (Servidor):** 
   Cada evento local o envío de mensaje incrementa de manera atómica el reloj local ($L_i = L_i + 1$). Por definición, en un mismo servidor, un evento posterior siempre tendrá un reloj con valor mayor al anterior.

2. **Orden entre procesos (Mensajes):** 
   Al enviar un mensaje en el tiempo $t_{envio}$, este se emite con dicha marca temporal. Al ser recibido por el servidor destino, este calcula $L_{destino} = \max(L_{local}, t_{envio}) + 1$. Esto garantiza que el evento de recepción tenga asignado un valor estrictamente mayor al evento de envío que lo originó ($C(recepción) > C(envío)$).

### Ejemplo práctico de la ejecución:
En `salida.txt`, el `Servidor-2` envía el mensaje `M2` con reloj **5**. El `Servidor-3` lo recibe con reloj **6** ($\max(0, 5) + 1 = 6$). Por lo tanto, se respeta la relación causal de envío antes de recepción ($5 < 6$).

---

## Referencias

Amazon Web Services. (s. f.). *Change data capture for DynamoDB Streams*. AWS Documentation. [https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.html](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.html)

Amazon Web Services. (s. f.). *Global tables core concepts*. AWS Documentation. [https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/globaltables-CoreConcepts.html](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/globaltables-CoreConcepts.html)

Apache Kafka. (s. f.). *Message format*. [https://kafka.apache.org/38/implementation/message-format/](https://kafka.apache.org/38/implementation/message-format/)

Apache Kafka. (s. f.). *Introduction*. [https://kafka.apache.org/082/documentation.html](https://kafka.apache.org/082/documentation.html)

MongoDB. (s. f.). *Causal consistency and read and write concerns*. MongoDB Manual. [https://www.mongodb.com/docs/manual/core/causal-consistency-read-write-concerns/](https://www.mongodb.com/docs/manual/core/causal-consistency-read-write-concerns/)

MongoDB. (s. f.). *Production notes: Clock synchronization*. MongoDB Manual.[https://www.mongodb.com/docs/manual/administration/production-notes/](https://www.mongodb.com/docs/manual/administration/production-notes/)

GeeksforGeeks. (2025, October 3). *Lamport’s logical clock*. GeeksforGeeks. [https://www.geeksforgeeks.org/dsa/lamports-logical-clock/](https://www.geeksforgeeks.org/dsa/lamports-logical-clock/)