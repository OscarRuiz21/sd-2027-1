# T04 · ¿Qué reloj usan las tecnologías que conozco?

**Nombre:** Vico [apellidos]  
**Materia:** Sistemas distribuidos  
**Fecha de entrega:** 7 de noviembre de 2026

## Introducción

En los sistemas distribuidos, las computadoras tienen relojes físicos independientes y no siempre marcan exactamente la misma hora. Aunque se use NTP para sincronizarlos, puede existir un desfase entre ellas. Por eso, las tecnologías distribuidas utilizan mecanismos adicionales para ordenar eventos, como números de secuencia, relojes lógicos o relojes híbridos.

En este trabajo analizo Apache Kafka, MongoDB y CockroachDB para explicar cómo ordenan sus operaciones y qué sucede cuando existen diferencias entre los relojes de sus servidores.

## 1. Apache Kafka

### ¿Qué es y para qué se usa?

Apache Kafka es una plataforma distribuida que permite transmitir, almacenar y procesar eventos. Se utiliza, por ejemplo, para enviar registros de aplicaciones, procesar compras en tiempo real y comunicar distintos servicios.

Los mensajes se organizan en *topics*, que se dividen en particiones para distribuir el trabajo entre varios servidores.

### ¿Qué tiempo utiliza para ordenar?

Kafka utiliza principalmente **números de secuencia llamados offsets**. Cada mensaje que se agrega a una partición recibe una posición dentro de su registro. El offset permite identificar su posición y leer los mensajes en orden.

Kafka también puede almacenar marcas de tiempo en los mensajes, pero estas no son el mecanismo principal que garantiza el orden de lectura dentro de una partición.

### ¿Qué orden garantiza?

Kafka garantiza el orden de los mensajes dentro de una misma partición. Un consumidor que lee esa partición observa los mensajes según el orden en que aparecen en el registro.

Si se utilizan varias particiones, no existe automáticamente un orden total entre todos los mensajes del topic. Para mantener juntos los eventos de una misma entidad, como las compras de un cliente, se puede utilizar una clave que los dirija a la misma partición.

### ¿Qué pasa cuando los relojes no coinciden?

El orden de lectura de una partición no depende de que todos los servidores tengan exactamente la misma hora, porque se basa en la posición de los mensajes en el registro.

NTP puede ayudar a mantener sincronizados los relojes físicos y las marcas de tiempo pueden servir para analizar cuándo ocurrió un evento. Sin embargo, no se debe utilizar la hora de pared como sustituto del orden garantizado por los offsets.

**En resumen:** Kafka utiliza offsets para ordenar mensajes dentro de cada partición, no un reloj lógico de Lamport para todos los mensajes del sistema.

## 2. MongoDB

### ¿Qué es y para qué se usa?

MongoDB es una base de datos NoSQL orientada a documentos. Almacena información en documentos BSON, una representación binaria de JSON. Se utiliza para guardar información de usuarios, productos, pedidos y otros datos que pueden tener estructuras flexibles.

### ¿Qué tiempo utiliza para ordenar?

MongoDB utiliza diferentes mecanismos según la operación.

- **Timestamp interno de BSON:** representa el tiempo en segundos junto con un ordinal que permite distinguir operaciones dentro del mismo segundo. Se utiliza, entre otras cosas, en el registro de operaciones de replicación (*oplog*).
- **ObjectId:** contiene una marca de tiempo en segundos, un valor aleatorio y un contador. Permite generar identificadores que suelen quedar aproximadamente ordenados por creación, pero no garantiza un orden cronológico exacto.
- **Tiempo lógico de operaciones:** MongoDB también utiliza marcas de tiempo lógicas, como `clusterTime`, para seguir el avance de las operaciones distribuidas y apoyar las garantías de consistencia causal.

Por tanto, MongoDB no depende de un único tipo de reloj para todas sus funciones.

### ¿Qué orden garantiza?

En un conjunto de réplicas, las réplicas secundarias aplican las operaciones en el mismo orden que la réplica primaria, aunque pueden llevar retraso.

Además, las sesiones con consistencia causal permiten respetar el orden de las operaciones que dependen unas de otras, cuando se utilizan las opciones de lectura y escritura requeridas.

Esto no significa que cualquier documento de toda la base de datos quede ordenado cronológicamente por su `ObjectId`. Tampoco significa que las operaciones independientes siempre tengan un orden causal entre sí.

### ¿Qué pasa cuando los relojes no coinciden?

Los `ObjectId` pueden reflejar diferencias entre los relojes de los clientes que los generan. Por eso, dos identificadores creados casi al mismo tiempo pueden no quedar ordenados de acuerdo con el instante real en que se crearon.

Para las operaciones distribuidas, MongoDB dispone de mecanismos de marcas de tiempo lógicas y consistencia causal que no se basan únicamente en comparar la hora física. Las opciones de lectura y escritura determinan las garantías obtenidas.

NTP puede reducir las diferencias entre relojes físicos, pero no sustituye estos mecanismos.

**En resumen:** MongoDB combina marcas de tiempo físicas, contadores y marcas de tiempo lógicas. La garantía depende de si hablamos de identificadores, replicación o consistencia causal.

## 3. CockroachDB

### ¿Qué es y para qué se usa?

CockroachDB es una base de datos SQL distribuida diseñada para almacenar datos en varios nodos y mantener la consistencia de las transacciones. Se utiliza en aplicaciones que necesitan disponibilidad, tolerancia a fallos y datos distribuidos entre diferentes servidores o ubicaciones.

### ¿Qué tiempo utiliza para ordenar?

CockroachDB utiliza **relojes lógicos híbridos**, conocidos como HLC (*Hybrid Logical Clocks*).

Un HLC combina dos componentes:

- **Componente físico:** se basa en la hora del sistema y se mantiene cerca del tiempo de pared.
- **Componente lógico:** permite distinguir eventos que tienen la misma parte física y mantener el avance lógico del reloj.

Cuando un nodo recibe una marca de tiempo de otro nodo, actualiza su propio HLC para incorporar esa información. De esta manera, puede representar relaciones causales sin depender exclusivamente de que los relojes físicos sean idénticos.

### ¿Qué orden garantiza?

CockroachDB asigna marcas de tiempo HLC a las transacciones y utiliza mecanismos de concurrencia y consistencia para ordenar sus operaciones.

Los HLC permiten seguir la causalidad entre nodos. Sin embargo, el HLC por sí solo no equivale a una garantía de orden global linealizable: esta depende también de los protocolos de transacción, la replicación y las condiciones de funcionamiento del sistema.

Cuando una operación depende de otra, CockroachDB puede utilizar información causal para asegurar que sus marcas de tiempo respeten esa dependencia.

### ¿Qué pasa cuando los relojes no coinciden?

CockroachDB está diseñado para funcionar con relojes físicos sincronizados de manera aproximada, por ejemplo, mediante NTP o servicios de sincronización de los proveedores de nube.

El sistema configura un límite para el desfase permitido entre relojes. Si se detectan problemas relacionados con ese límite, pueden producirse esperas, reintentos o errores, dependiendo de la situación y de la configuración.

El componente lógico del HLC ayuda a mantener el orden de los eventos relacionados, pero no elimina la necesidad de controlar el desfase físico para cumplir las garantías de consistencia del sistema.

**En resumen:** CockroachDB combina tiempo físico y lógico para asignar marcas de tiempo a las transacciones distribuidas y mantener sus garantías de consistencia.

## Comparación final

| Tecnología | Mecanismo principal de orden | Alcance de la garantía | Papel del reloj físico |
|---|---|---|---|
| Apache Kafka | Offsets de los mensajes | Dentro de una partición | Las marcas de tiempo son útiles, pero no determinan el orden del registro |
| MongoDB | Depende de la función: timestamps, orden de replicación y tiempo lógico | Replicación y operaciones causales, según la configuración | Puede afectar el orden aproximado de los ObjectId |
| CockroachDB | Reloj lógico híbrido (HLC) y protocolos de transacción | Transacciones distribuidas, según las garantías de consistencia | Es una parte del HLC y debe respetar los límites de desfase configurados |

## Conclusión

Las tres tecnologías muestran que ordenar eventos distribuidos no consiste simplemente en comparar la hora que marca cada computadora.

Kafka utiliza offsets para garantizar el orden dentro de una partición. MongoDB combina distintos mecanismos dependiendo de si necesita identificar documentos, replicar operaciones o mantener consistencia causal. CockroachDB utiliza relojes lógicos híbridos que combinan la hora física con un componente lógico y los integra con sus protocolos de transacción.

La principal diferencia es el alcance de las garantías: un orden dentro de una partición no es lo mismo que el orden causal entre operaciones, y ninguno de estos conceptos debe confundirse automáticamente con un orden global de todas las operaciones.

## Fuentes consultadas

1. Apache Software Foundation. (s. f.). *Introduction*. Apache Kafka. https://kafka.apache.org/documentation/#introduction
2. MongoDB, Inc. (s. f.). *BSON types*. MongoDB Manual. https://www.mongodb.com/docs/manual/reference/bson-types/
3. MongoDB, Inc. (s. f.). *Read isolation, consistency, and recency*. MongoDB Manual. https://www.mongodb.com/docs/manual/core/read-isolation-consistency-recency/
4. Cockroach Labs. (s. f.). *CockroachDB design documentation: Hybrid logical clock*. GitHub. https://github.com/cockroachdb/cockroach/blob/master/docs/design.md
5. Kulkarni, S., Demirbas, M., Madappa, D., Avva, B., & Leone, M. (2014). *Logical physical clocks and consistent snapshots in globally distributed databases*. https://cse.buffalo.edu/tech-reports/2014-04.pdf
