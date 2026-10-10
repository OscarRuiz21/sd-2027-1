# Tarea 3 - Una Implementacióin real de Sharding

## Tecnología elegida - MongoDB

Preguntas

1. Qué es y para qué se usa.
Escogí MongoDB ya que es un abase de datos NoSQL orientado a guardar los datos en formatos como JSON o similares, de manera que se usan para grandes volúnemes de datos o picos de transacciones altos, de manera que se puede escalar de forma horizontal de manera sencilla. Es muy utilizada en comercios online (E-commerce), redes sociales, análisis de datos masivos.

2. Cómo reparte los datos: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?
La tecnología MongoDB perimte dividir los datos en fragmentos lógicos llamados chunks, en donde se utilizan dos estrategias conocidas:
    -**Range-based Sharding:** agrupa ñps documentos basándose en valores continuos, el cual es ideal para consultas con rangos en datos.
    -**Hashed sharding:** Pasa la llave por una función matemática llamada hash y usa el resultado para asignar el dato, de forma que se distribuyen los datos de manera muy uniforme.

La llave es elegida por el desarrollador o administrador de la base de datos, ya que en el momento de habilitar el sharding en una colección, se tiene que recurrir a la implementaciónd de un comando donde se define cual será la Shard Key.

3. Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?
Al usar MongoDB, la aplicación nunca se conecta de forma directa a los shards, sino al enrutador llamado mongos. El mapa de qué chunk vive en qué shard se guarda en unos servidores llamados Config Servers.

En esta caso mongos es el que va a leer el mapa de los Config Servers, guadandolo en su memoria caché, por lo que al momento de llegar la consulta sabe perfectamente donde debe de ser redirigida en el shard, una vez que el shard responde, el mongo junta los resultados y los devuelve a la aplicación.

4. Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?
En este caso no se debe de mover todos los datos, ya que MongoDB tiene un proceso que corre en segundo plano (**Balancer**). El Balancer empieza a tomar chunks de los shards que están más llenos y los migra al nuevo shard, uno por uno. Este proceso ocurre en vivo, sin tirar la base de datos. Para que finalmente los shards lleguen a un número equilibrado.

5. Qué hace con un shard caliente, si hace algo.
MongoDB ya tiene una forma de manejar este concepto con algo llamado hot shards y depende de que estrategia se uso ya sea por rango o hash.

    -Si se elegío por rango un valor que siempre crece, todo el tráfico de escritura nuevo irá siempre al último shard, de manera que esto es lo que hace crear un hot shard, el Balancer intentará mover los chunks viejos a otros nodos, pero no podrá evitar el cuello de botella en las escrituras en tiempo real.
    -La documentación de MongoDB recomienda usar sharding por hash, si se genera un hot shard por repetición de valores, permite redefinir la Shard Key para agregar un segundo campo para poder romper ese chunk y redistribuirlo.

### Fuentes de consulta:
[1] MongoDB, Inc., "Sharding," *MongoDB Manual*. [Online]. Disponible: [https://www.mongodb.com/docs/manual/sharding/](https://www.mongodb.com/docs/manual/sharding/). [Consultado: Oct. 8, 2026].

[2] MongoDB, Inc., "Shard Keys," *MongoDB Manual*. [Online]. Disponible: [https://www.mongodb.com/es/docs/manual/core/sharding-shard-key/](https://www.mongodb.com/es/docs/manual/core/sharding-shard-key/). [Consultado: Oct. 9, 2026].

[3] MongoDB, Inc., "Manage Sharded Cluster Balancer," *MongoDB Manual*. [Online]. Disponible: [https://www.mongodb.com/es/docs/manual/core/sharding-balancer-administration/](https://www.mongodb.com/es/docs/manual/core/sharding-balancer-administration/). [Consultado: Oct. 8, 2026].

[4] MongoDB, Inc., "Data Partitioning with Chunks," *MongoDB Manual*. [Online]. Disponible: [https://www.mongodb.com/es/docs/manual/core/sharding-data-partitioning/](https://www.mongodb.com/es/docs/manual/core/sharding-data-partitioning/). [Consultado: Oct. 9, 2026].
