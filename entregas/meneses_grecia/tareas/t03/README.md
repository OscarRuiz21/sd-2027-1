# Una implementación real de sharding

_En la clase vimos el sharding en abstracto: la llave de partición, por rango o por hash, el ruteo y el shard caliente. Esta tarea es para ver cómo lo resuelve una tecnología de verdad._

## Data sharding
Sharding es una técnica empleada para escalar horizontalmente una aplicacion, donde una base de datos se divide en diferentes y se almacena en diferentes máquinas para tener un mejor control y agrandar la capacidad de almacenamiento de los datos. Estos fragmentos contienen una parte del estado total de la base de datos. Ademas, evita que ocurran cuellos de botella y eficientar el rendimiento y disponibilidad de las aplicaciones cuando múltiples usuarios hagan peticiones que hagan principalmente operaciones de lectura. Se ha convertido en cierta manera en una necesidad ahora que las aplicaciones manejan big data.

Para realizar este proceso, se necesita una clave

## Data Sharding en X, antes Twitter (anuncios)
Se toman los datos de dos blogs, que muestran dos ejemplos que se utilizan en esta red social: una para los posts a manera de ejercicio, y otra para los microservicios de publicidad (Shardlib).

Shardlib permite que un servicio cambie su esquema de particionado dinámicamente, sin tener que volver a desplegar todos los servicios clientes que se comunican con él. Uno de los servicios es  *ads-selection*, que mantiene índices en memoria para seleccionar anuncios candidatos, el cual se puede descomponer en 24 fragmentos, cada uno indexa aproximadamente una veinticuatroava parte de dichos índices. Con el siguiente esquema de hash + rangos:
+ Utiliza el ID del anunciante como clave de particionamiento.
+ Emplea una función *hash* para mapear el ID del anunciante en un espacio de claves *hash* comprendido entre [0, MAX_LONG].
+ Ese espacio de valores hash se divide en rangos contiguos, y cada rango se asigna a un shard. 
+ Los números del 0 al 23 representan los identificadores de partición (rango [0, N-1]). 
+ El particionamiento es basado en rangos, el cual divide el espacio de claves [0, MAX_LONG].

En sí, Shardlib no selecciona la llave automáticamente, sino proporciona la interfaz para que el usuario seleccione la clave

### Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?
Para encontrar el shard correcto, se utiliza *Placement*, una interfaz de Java que funciona como la fuente central de información sobre la distribución de los shards. El mapa entonces se representa mediante un archivo placement.json que el servicio puede consultar y con el cual rechazar conexiones que contiene:
- Una versión del esquema de particionado.
- Los identificadores de los shards.
- El rango hash que corresponde a cada shard.

No siempre se contacta con un único shard. Si un cliente representa un rango que se solapa con varios rangos del servicio de destino, puede ser necesario enviar solicitudes a varios shards. ShardLib calcula las rutas pertinentes según el pruning intent del cliente y los esquemas activos.

### Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?
Al agregar un shard, al ser una biblioteca dinámica, este cambia el esquema de sharding: se modifica el número de shards y los rangos hash uniformes asignados a cada uno. No necesariamente se mueve todo, y los datos puede que se deban redistribuir o volver a indexas.

### Qué hace con un shard caliente, si hace algo.
N/A. No hay información en el blog que lo especifique.

## Data Sharding en una red social como X, antes Twitter (posts)
Este en cambio, se concentra en la distribución de publicaciones en una red social. Propone combinar un identificador de publicación basado en el patrón Snowflake con hashing para equilibrar la distribución y facilitar las consultas ordenadas por fecha. 

La plataforma para el caso de estudio plantea una plataforma con aproximadamente 10.000 transacciones de lectura por segundo y una carga elevada de escritura. La técnica que se recomienda de la comparación con diferentes claves, es utilizar consistent hashing; ya que permite asignar los datos a esos servidores y facilita escalar horizontalmente al agregar o retirar nodos mediante el siguiente esquema:
+ Utilizar *hashing* de un ID de publicación conformado por timestamp y secuencia, para obtener una distribución relativamente uniforme y conservar un identificador ordenable por fecha (patrón Snowflake).
+ Utilizar solo hash(ID) % número_de_servidores genera problemas al agregar o eliminar servidores, porque cambia el resultado de la asignación para muchas claves. 
+ Genera el identificador y aplica hashing para determinar el shard. 


El equipo que diseña la aplicación elige la clave, cómo generar el ID y qué campos utilizar.

### Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?
El sistema necesita conocer la correspondencia entre las claves hash y los servidores disponibles.
1. Llega una consulta
2. Se calcula el hash de la llave y se obtiene una posición en el espacio hash.
3. Se consulta el esquema de distribución que identifica al servidor con la posición.
4. Se envía la consulta al shard, el servidor busca la publicación solicitada.

No se especifica quién guarda el mapa, pero la información puede mantenerse en la configuración del cliente, en una biblioteca de enrutamiento o en un servicio de metadatos compartido. 

### Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?
El consistent hashing reduce la cantidad de datos que necesitan reasignarse cuando se agrega o elimina un servidor. En lugar de recalcular simplemente hash(key) % N para todas las claves, el hashing consistente asigna las claves según su posición en un espacio hash. Al incorporar un nodo, normalmente solo las claves de determinadas regiones deben cambiar de propietario.

### Qué hace con un shard caliente, si hace algo.
Depende de la clave de sharding que se elige, hay diferentes estrategias.
| Clave | Riesgo |
|----------|----------|
| Tweet ID   | Distribuye las publicaciones de manera relativamente uniforme, pero una publicación muy consultada puede concentrar lecturas en un shard.   |
| User ID   | Un usuario muy popular puede generar mucho más tráfico que los demás.  |
| Timestamp | Las publicaciones recientes pueden concentrar la carga en determinadas particiones. |
| ID con timestamp y secuencia	| Busca una distribución más uniforme y facilita ordenar por fecha, reduciendo concentración de tráfico. |

El hashing consistente facilita la incorporación y retirada de servidores, pero no garantiza que una clave popular deje de generar una carga desproporcionada. Adicionalmente, se podrían replicar los datos para repartir las lecturas, utilizar caché para consultas repetidas y/o dividir las operaciones de una clave popular entre varias particiones.

## Referencias

1. [Database Sharding: Concepts and Examples](https://www.mongodb.com/resources/products/capabilities/database-sharding-explained)
2. [Sharding, simplification, and Twitter’s ads serving platform](https://blog.x.com/engineering/en_us/topics/infrastructure/2021/sharding-simplification-and-twitters-ads-serving-platform)
3. [Data Sharding: Twitter Posts](https://www.rkenmi.com/posts/deep-dive-into-data-sharding-choices)