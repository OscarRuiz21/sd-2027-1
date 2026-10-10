# T03: Una implementación de Sharding - MongoDB

## Qué es y para qué se usa
MongoDB es una base de datos NoSQL orientada a documentos (JSON/BSON). Su implementación de sharding se utiliza para escalar horizontalmente bases de datos que superan los límites físicos de almacenamiento o de memoria RAM de un solo servidor. Es ideal para aplicaciones con cargas masivas de lectura y escritura, como analítica de Big Data, catálogos de e-commerce o registros de telemetría.

## Cómo reparte los datos
MongoDB reparte los datos agrupándolos en bloques lógicos llamados *Chunks* (por defecto de 64 MB). Soporta dos estrategias principales:
* **Por Rango (Range-based):** Agrupa valores cercanos. Es excelente para consultas de intervalos, pero propenso a cuellos de botella si los datos crecen en una sola dirección.
* **Por Hash (Hashed):** Pasa la llave por una función hash. Destruye la contigüidad de los datos, pero garantiza que las escrituras se distribuyan de forma perfectamente uniforme.

**¿Quién elige la llave?** El desarrollador o arquitecto de datos. Se define obligatoriamente al momento de fragmentar la colección y, por diseño, la llave elegida es inmutable.

## Cómo encuentra el shard correcto
Utiliza una arquitectura de tres componentes donde el ruteo está separado del almacenamiento. El cliente nunca se conecta directo a los datos, sino a un enrutador llamado **`mongos`**. 
El `mongos` no tiene estado propio; para saber a dónde ir, consulta a los **Config Servers** (Servidores de Configuración). Los Config Servers son los verdaderos dueños del mapa (metadatos) que dicta qué rangos de llaves viven en qué shard. El `mongos` guarda este mapa en caché y redirige la consulta directamente al servidor físico correcto.

## Qué pasa cuando agregas un shard
Al agregar un nuevo shard al clúster, los datos **sí se mueven**. MongoDB cuenta con un proceso en segundo plano llamado **Balancer**. Cuando el Balancer nota que un shard tiene muchos más *Chunks* que el shard recién agregado, comienza a migrar bloques de 64 MB a través de la red hacia el nuevo nodo. Este proceso ocurre en caliente (sin tirar el sistema) y se detiene automáticamente cuando los chunks están distribuidos de manera equitativa.

## Qué hace con un shard caliente
Si se configura un sharding por rango con una llave monótonamente creciente (como un timestamp), todas las escrituras nuevas siempre irán al último shard (hot shard). MongoDB intentará aliviarlo moviendo chunks antiguos mediante el Balancer, pero las escrituras seguirán asfixiando al nodo activo. 
Además, si muchas operaciones ocurren sobre una misma llave exacta, ese bloque superará los 64 MB y no podrá dividirse, convirtiéndose en un **Jumbo Chunk**. Por seguridad, el sistema marca el Jumbo Chunk y prohíbe moverlo. En la práctica, MongoDB no "resuelve" mágicamente el shard caliente; exige prevenirlo desde el diseño utilizando sharding por Hash.

## Fuentes
* MongoDB, Inc. (2026). *Sharding Concepts*. MongoDB Manual. Recuperado el 9 de octubre de 2026, de https://www.mongodb.com/docs/manual/sharding/
* MongoDB, Inc. (2026). *Data Partitioning with Chunks*. MongoDB Manual. Recuperado el 9 de octubre de 2026, de https://www.mongodb.com/docs/manual/core/sharding-data-partitioning/