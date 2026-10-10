# T03 · Una implementación real de sharding: MongoDB

El particionamiento horizontal o *sharding* es una técnica central en los sistemas distribuidos para distribuir grandes volúmenes de datos y carga de trabajo entre múltiples nodos independientes, permitiendo escalar el sistema más allá de los límites de una sola máquina.

A continuación, se analiza cómo implementa este concepto **MongoDB**, uno de los sistemas de bases de datos distribuidas más representativos.

---

## 1. ¿Qué es y para qué se usa?

**MongoDB** es un sistema gestor de bases de datos NoSQL orientado a documentos (formato BSON). Se utiliza para aplicaciones que requieren alta disponibilidad, tolerancia a fallos y, principalmente, **escalabilidad horizontal**.

En lugar de depender exclusivamente de escalar verticalmente (aumentar CPU, RAM o disco de un único servidor), MongoDB implementa clústeres fragmentados (*sharded clusters*) para repartir colecciones de datos masivas y peticiones concurrentes entre múltiples instancias de almacenamiento independientes.

---

## 2. ¿Cómo reparte los datos?

MongoDB organiza y fragmenta las colecciones en bloques lógicos denominados **chunks** (fragmentos de documentos contiguos según el valor de la clave).

* **Estrategias de partición:**
  * **Por rango (*Range-based sharding*):** Los documentos se agrupan según rangos continuos del valor de la clave. Facilita consultas por intervalo, pero puede provocar que inserciones monotónicas (como fechas o IDs autoincrementales) se concentren en un único shard.
  * **Por hash (*Hashed sharding*):** Se calcula un valor hash (MD5) del campo elegido y los datos se reparten según este valor. Garantiza una distribución uniforme y aleatoria de las escrituras, aunque penaliza las consultas por rango.
* **Elección de la llave:** La llave de partición (**shard key**) la elige de manera explícita el **desarrollador o administrador del sistema** al momento de configurar la colección fragmentada. Esta decisión es crítica, ya que determina el rendimiento y la distribución de las cargas en el clúster.

---

## 3. ¿Cómo encuentra el shard correcto al recibir una consulta?

La arquitectura de un clúster fragmentado en MongoDB se divide en tres componentes principales:

1. **Enrutadores (`mongos`):** Actúan como intermediarios entre las aplicaciones cliente y los nodos de almacenamiento. El cliente nunca se conecta directamente a los shards.
2. **Servidores de configuración (`Config Servers`):** Conjunto de réplicas dedicado que almacena de forma consistente los **metadatos del clúster** y el catálogo de enrutamiento (el mapa que indica qué rangos de datos o *chunks* residen en cada shard).
3. **Nodos de datos (`Shards`):** Cada shard es un conjunto de réplicas (*replica set*) encargado de almacenar y persistir los documentos.

**Flujo de consulta:**
* Al recibir una consulta, la instancia `mongos` consulta su copia local en caché de los metadatos (previamente sincronizada desde los *Config Servers*).
* Si la consulta incluye la *shard key*, el enrutador realiza una **consulta dirigida (*targeted query*)** enviando la petición únicamente al shard específico que contiene el dato.
* Si la consulta no incluye la *shard key*, el enrutador debe enviar la solicitud a todos los shards del clúster (**consulta de difusión o *scatter-gather***) y combinar los resultados antes de responder al cliente.

---

## 4. ¿Qué pasa cuando se agrega un shard?

Cuando un administrador añade un nuevo shard al clúster (mediante `sh.addShard()`):

* **Detección y rebalanceo:** El componente interno conocido como **Balancer** (balanceador) detecta que existe una disparidad en la cantidad de *chunks* entre los shards existentes y el nodo recién integrado.
* **Movimiento de datos:** Los datos **sí se mueven**, pero **no se transfieren todos**. Solo se migra una cantidad proporcional de *chunks* desde los shards con mayor carga hacia el nuevo shard hasta restablecer el equilibrio del clúster.
* **Disponibilidad continua:** El proceso de migración de *chunks* se ejecuta en segundo plano. Durante la transferencia, las operaciones de lectura y escritura sobre dichos datos continúan funcionando con normalidad.

---

## 5. ¿Qué hace con un shard caliente (*hotspot*)?

Un **shard caliente** ocurre cuando un shard específico recibe un volumen desproporcionado de lecturas o escrituras respecto a los demás, usualmente debido a una elección inadecuada de la *shard key* (por ejemplo, llaves con baja cardinalidad o valores monotónicamente crecientes en partición por rango).

MongoDB aborda este problema a través de diferentes mecanismos:

* **División de chunks (*Chunk Splitting*):** Si un *chunk* crece más allá del tamaño configurado (por defecto 64 MB), MongoDB lo divide automáticamente en dos o más fragmentos más pequeños. Si los fragmentos resultantes tienen valores de clave distintos, el *balancer* puede redistribuirlos a otros shards.
* **Recomendación arquitectónica:** Para prevenir la concentración de escrituras, la práctica estándar recomendada es utilizar **Hashed Sharding** o llaves compuestas con alta cardinalidad.
* **Herramientas de remediación:** En versiones modernas, MongoDB incluye operaciones administrativas para mitigar el desbalance sin reconstruir la colección desde cero:
  * `refineCollectionShardKey`: Permite añadir un sufijo a la llave existente para aumentar su cardinalidad.
  * `reshardCollection`: Permite redefinir por completo la *shard key* de una colección en vivo, redistribuyendo todos los datos del clúster en segundo plano.

---

## Fuentes

1. MongoDB, Inc. (2024). *Sharding — MongoDB Manual*. Documentación oficial. Disponible en: https://www.mongodb.com/docs/manual/sharding/
2. Kleppmann, M. (2017). *Designing Data-Intensive Applications: The Big Ideas Behind Reliable, Scalable, and Maintainable Systems*. O'Reilly Media. Capítulo 6: Partitioning.
3. Plugge, E., Hawkins, T., & Membrey, P. (2010). *The Definitive Guide to MongoDB: The NoSQL Database for Cloud and Desktop Computing*. Apress.
