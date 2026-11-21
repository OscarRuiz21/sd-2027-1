# T04 · ¿Qué reloj usan las tecnologías que conozco?

**Nombre:** Victoria Monserrat Muñoz San Agustin
**Materia:** Sistemas Distribuidos  
**Fecha:** 7 de noviembre de 2026

## Introducción

En los sistemas distribuidos, las computadoras tienen relojes físicos independientes y no siempre marcan exactamente la misma hora. Aunque se utilice NTP para sincronizarlos, puede existir un desfase entre ellas.

Por esta razón, las tecnologías distribuidas utilizan diferentes mecanismos para ordenar eventos, como números de secuencia, relojes lógicos y relojes híbridos.

En este trabajo analizo tres tecnologías: Apache Kafka, MongoDB y CockroachDB. El objetivo es conocer qué mecanismos utilizan para ordenar sus operaciones, qué garantías ofrecen y qué sucede cuando los relojes de sus servidores no coinciden.

\---

## 1\. Apache Kafka

### ¿Qué es y para qué se usa?

Apache Kafka es una plataforma distribuida que permite transmitir, almacenar y procesar eventos. Se utiliza para enviar registros de aplicaciones, procesar compras en tiempo real y comunicar diferentes servicios.

Los mensajes se organizan en *topics*, que se dividen en particiones para distribuir el trabajo entre varios servidores.

### ¿Qué tiempo utiliza para ordenar?

Kafka utiliza principalmente números de secuencia llamados **offsets**.

Cada mensaje que se agrega a una partición recibe una posición dentro de su registro. El offset permite identificar su posición y leer los mensajes en el orden en que fueron agregados.

Kafka también puede almacenar marcas de tiempo en los mensajes, pero estas no son el mecanismo principal que garantiza el orden dentro de una partición.

### ¿Qué orden garantiza?

Kafka garantiza el orden de los mensajes dentro de una misma partición.

Si se utilizan varias particiones, no existe automáticamente un orden total entre todos los mensajes del topic.

Por ejemplo, si una aplicación registra las compras de diferentes clientes, puede utilizar una clave para que los mensajes de un mismo cliente lleguen a la misma partición y conserven su orden.

### ¿Qué pasa cuando los relojes no coinciden?

El orden de lectura de una partición no depende de que todos los servidores tengan exactamente la misma hora, porque se basa en la posición de los mensajes en el registro.

NTP puede ayudar a mantener sincronizados los relojes físicos y las marcas de tiempo pueden servir para analizar cuándo ocurrió un evento. Sin embargo, la hora de pared no sustituye el orden garantizado por los offsets.

**En resumen:** Kafka utiliza offsets para ordenar mensajes dentro de cada partición. No necesita utilizar un reloj lógico de Lamport para garantizar ese orden.

\---

## 2\. MongoDB

### ¿Qué es y para qué se usa?

MongoDB es una base de datos NoSQL orientada a documentos. Almacena información en documentos BSON, una representación binaria de JSON.

Se utiliza para guardar información de usuarios, productos, pedidos y otros datos que pueden tener estructuras flexibles.

### ¿Qué tiempo utiliza para ordenar?

MongoDB utiliza diferentes mecanismos dependiendo de la operación.

* **Timestamp interno de BSON:** representa el tiempo en segundos junto con un ordinal que permite distinguir operaciones dentro del mismo segundo. Se utiliza, entre otras cosas, en el registro de operaciones de replicación (*oplog*).
* **ObjectId:** contiene una marca de tiempo en segundos, un valor aleatorio y un contador. Permite generar identificadores que suelen quedar aproximadamente ordenados por creación, pero no garantiza un orden cronológico exacto.
* **Tiempo lógico de operaciones:** MongoDB utiliza marcas de tiempo lógicas, como `clusterTime`, para seguir el avance de las operaciones distribuidas y apoyar las garantías de consistencia causal.

Por lo tanto, MongoDB no depende de un único tipo de reloj para todas sus funciones.

### ¿Qué orden garantiza?

En un conjunto de réplicas, las réplicas secundarias aplican las operaciones en el mismo orden que la réplica primaria, aunque pueden llevar retraso.

Además, las sesiones con consistencia causal permiten respetar el orden de las operaciones que dependen unas de otras, cuando se utilizan las opciones de lectura y escritura requeridas.

Esto no significa que todos los documentos de la base de datos queden ordenados cronológicamente por su `ObjectId`. Tampoco significa que las operaciones independientes siempre tengan un orden causal entre sí.

### ¿Qué pasa cuando los relojes no coinciden?

Los `ObjectId` pueden reflejar diferencias entre los relojes de los clientes que los generan. Por eso, dos identificadores creados casi al mismo tiempo pueden no quedar ordenados de acuerdo con el instante real en que se crearon.

Para las operaciones distribuidas, MongoDB dispone de mecanismos de marcas de tiempo lógicas y consistencia causal que no se basan únicamente en comparar la hora física.

NTP puede reducir las diferencias entre relojes físicos, pero no sustituye estos mecanismos.

**En resumen:** MongoDB combina marcas de tiempo físicas, contadores y marcas de tiempo lógicas. Las garantías dependen de si hablamos de identificadores, replicación o consistencia causal.

\---

## 3\. CockroachDB

### ¿Qué es y para qué se usa?

CockroachDB es una base de datos SQL distribuida diseñada para almacenar datos en varios nodos y mantener la consistencia de las transacciones.

Se utiliza en aplicaciones que necesitan disponibilidad, tolerancia a fallos y datos distribuidos entre diferentes servidores o ubicaciones.

### ¿Qué tiempo utiliza para ordenar?

CockroachDB utiliza **relojes lógicos híbridos**, conocidos como HLC (*Hybrid Logical Clocks*).

Un HLC combina dos componentes:

* **Componente físico:** se basa en la hora del sistema y se mantiene cerca del tiempo de pared.
* **Componente lógico:** permite distinguir eventos que tienen la misma parte física y mantener el avance lógico del reloj.

Cuando un nodo recibe una marca de tiempo de otro nodo, actualiza su propio HLC para incorporar esa información.

De esta manera, puede representar relaciones causales sin depender exclusivamente de que los relojes físicos sean idénticos.

### ¿Qué orden garantiza?

CockroachDB asigna marcas de tiempo HLC a las transacciones y utiliza mecanismos de concurrencia y consistencia para ordenar sus operaciones.

Los HLC permiten seguir la causalidad entre nodos. Sin embargo, el HLC por sí solo no equivale a una garantía de orden total linealizable: esta depende también de los protocolos de transacción, la replicación y las condiciones de funcionamiento del sistema.

Cuando una operación depende de otra, CockroachDB puede utilizar información causal para asegurar que sus marcas de tiempo respeten esa dependencia.

### ¿Qué pasa cuando los relojes no coinciden?

CockroachDB está diseñado para funcionar con relojes físicos sincronizados de manera aproximada, por ejemplo, mediante NTP o servicios de sincronización de los proveedores de nube.

El sistema configura un límite para el desfase permitido entre relojes. Si se detectan problemas relacionados con ese límite, pueden producirse esperas, reintentos o errores, dependiendo de la situación y de la configuración.

El componente lógico del HLC ayuda a mantener el orden de los eventos relacionados, pero no elimina la necesidad de controlar el desfase físico para cumplir las garantías de consistencia del sistema.

**En resumen:** CockroachDB combina tiempo físico y lógico para asignar marcas de tiempo a las transacciones distribuidas y mantener sus garantías de consistencia.

\---

## 4\. Comparación final

|Tecnología|Mecanismo principal de orden|Alcance de la garantía|Papel del reloj físico|
|-|-|-|-|
|Apache Kafka|Offsets de los mensajes|Dentro de una partición|Las marcas de tiempo son útiles, pero no determinan el orden del registro|
|MongoDB|Timestamps, orden de replicación y tiempo lógico|Replicación y operaciones causales, según la configuración|Puede afectar el orden aproximado de los ObjectId|
|CockroachDB|Reloj lógico híbrido (HLC) y protocolos de transacción|Transacciones distribuidas, según las garantías de consistencia|Es parte del HLC y debe respetar los límites de desfase configurados|

\---

## 5\. Conclusión

Las tres tecnologías muestran que ordenar eventos distribuidos no consiste simplemente en comparar la hora que marca cada computadora.

Kafka utiliza offsets para garantizar el orden dentro de una partición. MongoDB combina distintos mecanismos dependiendo de si necesita identificar documentos, replicar operaciones o mantener consistencia causal. CockroachDB utiliza relojes lógicos híbridos que combinan la hora física con un componente lógico y los integra con sus protocolos de transacción.

La principal diferencia es el alcance de las garantías: un orden dentro de una partición no es lo mismo que el orden causal entre operaciones, y ninguno de estos conceptos debe confundirse automáticamente con un orden global de todas las operaciones.

\---

## 6\. Opcional: Simulación de relojes de Lamport en Java

### Descripción

Se desarrolló un programa en Java para simular tres servidores distribuidos (A, B y C) que intercambian cuatro mensajes utilizando relojes lógicos de Lamport.

Cada servidor tiene su propio reloj implementado mediante `AtomicLong`, que permite incrementar y actualizar el contador de manera segura.

### Funcionamiento

El programa implementa las siguientes reglas:

1. **Evento local:** el reloj se incrementa en una unidad.
2. **Envío de mensaje:** el servidor incrementa su reloj y adjunta el valor al mensaje.
3. **Recepción de mensaje:** el servidor actualiza su reloj utilizando la fórmula:

   `reloj = max(reloj\_local, reloj\_recibido) + 1`

### Flujo de comunicación

La simulación sigue esta secuencia:

1. El servidor A inicia un evento local.
2. A envía una solicitud de información a B.
3. B recibe la solicitud y la reenvía a C.
4. C recibe el mensaje y responde a A.
5. A recibe la respuesta y envía una confirmación a C.
6. C recibe la confirmación.

En total se intercambian cuatro mensajes entre los tres servidores.

### ¿Por qué se respeta la causalidad?

Los relojes de Lamport permiten representar el orden causal de los eventos.

Cuando un servidor envía un mensaje, adjunta el valor de su reloj lógico. Al recibirlo, el servidor destinatario actualiza su propio reloj para que sea mayor que el valor recibido.

Por ejemplo:

* A envía un mensaje con reloj 2.
* B recibe el mensaje y actualiza su reloj a 3.
* B envía un mensaje con reloj 4.
* C recibe el mensaje y actualiza su reloj a 5.

Los valores de los relojes respetan la relación causal: si un evento ocurre antes y puede influir en otro, su marca de tiempo lógica será menor.

Esto demuestra que los relojes de Lamport permiten ordenar eventos relacionados sin necesitar que los relojes físicos de los servidores estén sincronizados.

**Importante:** una marca de tiempo menor indica precedencia causal cuando existe esa relación, pero no significa necesariamente que todos los eventos con marcas diferentes estén causalmente relacionados.

### Resultado de la ejecución

El programa produjo los siguientes valores finales:

|Servidor|Reloj final|
|-|-:|
|Servidor A|8|
|Servidor B|4|
|Servidor C|9|

Los archivos correspondientes a esta simulación se encuentran en la carpeta `lamport/`:

* `Main.java`: código fuente de la simulación.
* `SALIDA.txt`: resultado de la ejecución del programa.

\---

## 7\. Fuentes consultadas

1. Apache Software Foundation. (s. f.). *Introduction*. Apache Kafka. https://kafka.apache.org/documentation/#introduction
2. MongoDB, Inc. (s. f.). *BSON types*. MongoDB Manual. https://www.mongodb.com/docs/manual/reference/bson-types/
3. MongoDB, Inc. (s. f.). *Read isolation, consistency, and recency*. MongoDB Manual. https://www.mongodb.com/docs/manual/core/read-isolation-consistency-recency/
4. Cockroach Labs. (s. f.). *CockroachDB design documentation: Hybrid logical clock*. GitHub. https://github.com/cockroachdb/cockroach/blob/master/docs/design.md
5. Kulkarni, S., Demirbas, M., Madappa, D., Avva, B., \& Leone, M. (2014). *Logical physical clocks and consistent snapshots in globally distributed databases*. https://cse.buffalo.edu/tech-reports/2014-04.pdf

