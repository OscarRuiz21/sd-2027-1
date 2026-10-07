## T03 · Una implementación real de sharding: MongoDB 


**1. ¿Qué es y para qué se usa?**

MongoDB es una base de datos no relacional de código abierto. En lugar de filas y columnas, guarda la información en documentos parecidos a JSON, formados por campos y valores. Esto es útil cuando los datos varían mucho de un registro a otro, por ejemplo en catálogos de productos o perfiles de usuarios. Cuando los datos o el tráfico crecen más de lo que aguanta un solo servidor, MongoDB usa sharding reparte cada colección entre varios servidores llamados shards.

**2. Cómo reparte los datos: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?**

MongoDB reparte los documentos según una shard key (llave de partición), que es uno o más campos del documento. Tiene dos estrategias:

- Por rango. Los documentos con valores de llave cercanos quedan juntos. Esto hace eficientes las consultas por intervalo, pero si la llave crece siempre, todas las escrituras nuevas caen en el mismo shard.
- Por hash. MongoDB calcula un hash del valor de la llave y reparte con base en eso. La distribución es mucho más pareja, pero se pierden las consultas por rango eficientes.

La llave es seleccionada por el DBA, colección por colección, al activar el sharding. Internamente los datos se agrupan en chunks, que son rangos contiguos de valores de la llave, y cada chunk vive en un shard.

**3. Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?**

Un clúster con sharding cuenta con tres componentes.

- _Shards._ Almacena los datos.
- _mongos._ Routers a los que se conectan las aplicaciones.
- _Config servers._ Guardan el mapa de qué chunk vive en que shard.

Al recibir una consulta, mongos consulta el mapa de los config servers, identifica el shard que tiene los datos y le manda la consulta. Si la consulta incluye la shard key, va solo al shard o shards necesarios. Si no la incluye, el mongos tiene que preguntar a todos los shards (scatter-gather), este último método es más lento.

**4. Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?**

Un shard nuevo entra vacío, por lo que el clúster queda desbalanceado. En segundo plano, l balanceador empieza a mover chunks de los shards más llenos al nuevo,hasta que todos tengan una cantidad parecida. No se mueven todos los datos solo la porción necesaria para igualar.En ningún momento deja de funcionar el clúster El clúster sigue funcionando durante todo el proceso, aunque el balanceo completo puede tardar en completarse.

**5. Qué hace con un shard caliente, si hace algo.**

MongoDB no resuelve como tal un shard caliente ya que el balanceador reparte la cantidad de chunks, no el tráfico de datos. Uno de los casos típicos es una llave que crece de forma monótona con rangos así que todas las inserciones llegan al último chunk, y por lo tanto a un solo shard.

Sin emabargo, en MongoDB se puede elegir una llave hasheada  o una llave compuesta que distribuya mejor las escrituras, Resharding para cambiar la shard key de una colección que ya tiene datos y está repartida entre shards o ones para controlar manualmente qué rangos de datos van a qué shards.

## Fuentes

- [Balanceador del clúster particionado](https://www.mongodb.com/es/docs/manual/core/sharding-balancer-administration/). MongoDB. Accedido el 4 de octubre de 2026.
- [Shard Keys](https://www.mongodb.com/es/docs/manual/core/sharding-shard-key/). MongoDB. Accedido el 4 de octubre de 2026.
- [Particionado](https://www.mongodb.com/es/docs/manual/sharding/). MongoDB. Accedido el 5 de octubre de 2026.
- [Agregar particiones a un clúster](https://www.mongodb.com/es/docs/manual/tutorial/add-shards-to-shard-cluster/). MongoDB. Accedido el 4 de octubre de 2026.
- [Repartición de una colección](https://www.mongodb.com/es/docs/manual/core/sharding-reshard-a-collection/). MongoDB. Accedido el 5 de octubre de 2026.
