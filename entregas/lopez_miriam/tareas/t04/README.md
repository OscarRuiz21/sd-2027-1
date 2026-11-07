# T04 - ¿Qué reloj usan las tecnologías que conoces?

## Introducción

En esta tarea investigué tres tecnologías que se utilizan en sistemas distribuidos: Apache Kafka, MongoDB y Redis Streams. El objetivo es conocer cómo ordenan sus operaciones y qué hacen cuando los relojes de los servidores no coinciden.

## 1. Apache Kafka

**¿Qué es y para qué se utiliza?** Kafka permite enviar, almacenar y procesar eventos entre aplicaciones. Por ejemplo, en un banco puede comunicar que se realizó una transferencia para que otro servicio genere una notificación.

**¿Qué tiempo utiliza para ordenar?** Utiliza números de posición llamados *offsets*, asignados a los mensajes dentro de cada partición. Los mensajes también pueden tener marcas de tiempo físicas, pero el orden del registro se establece por sus offsets.

**¿Qué orden garantiza?** Mantiene el orden de los mensajes dentro de una misma partición, no entre todas las particiones del sistema.

**¿Qué pasa cuando los relojes no coinciden?** La diferencia entre horas físicas no altera el orden por offsets. Kafka no necesita sincronizar los relojes con NTP para determinar qué mensaje ocupa primero una posición dentro de una partición.

## 2. MongoDB

**¿Qué es y para qué se utiliza?** MongoDB es una base de datos NoSQL que guarda información en documentos. Puede utilizarse para registrar usuarios, movimientos y notificaciones.

**¿Qué tiempo utiliza para ordenar?** En clústeres con replicación o fragmentación utiliza tiempo lógico, como `clusterTime`, para dar seguimiento al avance de las operaciones. No equivale a usar solamente la hora del reloj de pared.

**¿Qué orden garantiza?** Puede conservar relaciones causales entre operaciones de una sesión cuando se configura la consistencia causal y se emplean las opciones adecuadas de lectura y escritura. Esto no implica un orden global automático para todas las operaciones independientes.

**¿Qué pasa cuando los relojes no coinciden?** Para seguir el avance causal comparte y actualiza información de tiempo lógico entre participantes. Así no necesita que las horas físicas sean idénticas para ese propósito.

## 3. Redis Streams

**¿Qué es y para qué se utiliza?** Redis Streams permite guardar mensajes o eventos en un registro al que se pueden agregar entradas, por ejemplo para registrar notificaciones.

**¿Qué tiempo utiliza para ordenar?** Sus identificadores generados automáticamente combinan milisegundos del reloj del servidor y un número de secuencia. Un ejemplo es `1692632147973-0`.

**¿Qué orden garantiza?** Los IDs de un mismo stream son crecientes, por lo que las entradas tienen un orden dentro de ese stream. No se establece automáticamente un único orden entre streams distintos. Además, varios consumidores pueden terminar de procesar mensajes en otro orden.

**¿Qué pasa cuando los relojes no coinciden?** Si el reloj retrocede, Redis utiliza el valor temporal del último ID y aumenta la parte de secuencia, de modo que el siguiente ID sigue siendo mayor. No depende de sincronización perfecta con NTP para conservar esta propiedad.

## Conclusión

Comprendí que no todos los sistemas distribuidos utilizan el mismo mecanismo para ordenar operaciones. Kafka usa offsets por partición, MongoDB maneja tiempo lógico para seguir relaciones causales y Redis Streams combina tiempo físico con una secuencia. Lo que más me llamó la atención es que el orden puede mantenerse sin que todos los servidores tengan exactamente la misma hora.

## Opcional: reloj de Lamport en Java

En `lamport/Lamport.java` simulé tres servidores (A, B y C) que intercambian cuatro mensajes. Cada servidor guarda su reloj en un `AtomicLong`. En un evento local o envío incrementa el reloj en uno; al recibir un mensaje actualiza su reloj con `max(relojLocal, relojRecibido) + 1`.

Para ejecutar desde la carpeta `lamport/`:

```bash
javac Lamport.java
java Lamport
```

La salida de ejemplo se encuentra en `lamport/salida.txt`.

**¿Por qué respeta la causalidad?** Cada recepción tiene un reloj mayor que el del envío correspondiente: 2→3, 5→6, 7→8 y 9→10. Además, los eventos de cada servidor hacen avanzar su reloj. Por eso, si un evento influye en otro mediante un mensaje, el primero recibe una marca lógica menor. Esto no significa que cualquier par de eventos con marcas diferentes tenga necesariamente una relación causal.

## Fuentes consultadas

- Apache Kafka, *Introduction*: https://kafka.apache.org/intro
- Apache Kafka, *Protocol*: https://kafka.apache.org/protocol.html
- MongoDB, *Causal Consistency Specification*: https://specifications.readthedocs.io/en/latest/causal-consistency/causal-consistency/
- Redis, *Redis Streams*: https://redis.io/docs/latest/develop/data-types/streams/
- Redis, *XADD*: https://redis.io/docs/latest/commands/xadd/
