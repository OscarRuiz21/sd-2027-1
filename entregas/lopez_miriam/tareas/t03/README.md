# T03 - Una implementación real de sharding

**Tecnología elegida:** MongoDB

## 1. ¿Qué es y para qué se usa?

MongoDB es una base de datos NoSQL de código abierto que cuenta con sharding nativo. Es conocida por su facilidad de uso y escalabilidad. Es útil para manejar grandes cantidades de datos no estructurados o semiestructurados, que se almacenan en documentos similares a JSON.

## 2. ¿Cómo reparte los datos?

MongoDB fragmenta los datos a nivel de colección y los distribuye entre los diferentes shards del clúster. Para hacerlo utiliza una clave de fragmentación, llamada *shard key*, que consiste en uno o varios campos de los documentos.

Puede repartir la información de dos formas principales:

- **Por rangos:** agrupa los datos dependiendo de sus valores. Por ejemplo, números de cuenta del 1 al 1000 en un grupo y del 1001 al 2000 en otro.
- **Por hash:** utiliza una función que transforma el valor de la llave en otro número, que sirve para distribuir los datos.

La llave de partición la elige el desarrollador o administrador al configurar la colección. MongoDB utiliza esa llave para organizar la información en fragmentos llamados *chunks*.

## 3. ¿Cómo encuentra el shard correcto?

Se utiliza un componente llamado `mongos`, que se encarga de dirigir las consultas al shard correspondiente.

Para saber dónde está la información, utiliza los datos de los servidores de configuración (*config servers*), que guardan un mapa con la ubicación de los fragmentos. `mongos` también conserva una copia de esa información para realizar las consultas.

Por ejemplo, si buscamos un alumno mediante su número de cuenta y este campo es la llave de partición, `mongos` puede identificar en qué shard se encuentra. Si la consulta no permite identificarlo, puede necesitar consultar varios shards.

## 4. ¿Qué pasa cuando agregas un shard?

Cuando agregamos un nuevo shard, MongoDB puede comenzar a redistribuir la información para aprovechar el espacio y los recursos del nuevo servidor.

Para hacerlo utiliza un proceso llamado *balancer*, que mueve fragmentos de información entre los shards.

No necesariamente mueve todos los datos ni una cantidad fija. Esto depende de cómo se encuentre distribuida la información y de cuántos fragmentos necesite mover para equilibrar el sistema.

## 5. ¿Qué hace con un shard caliente?

Un shard caliente es uno que recibe muchas más consultas o escrituras que los demás, por lo que puede trabajar más lento y afectar el rendimiento.

MongoDB puede usar el *balancer* para repartir mejor los datos entre shards, pero esto no siempre soluciona el problema. Por ejemplo, si muchas consultas se dirigen a la misma llave de partición, pueden seguir llegando al mismo shard. Por eso es importante elegir bien la *shard key* para evitar que toda la carga se concentre en un solo lugar.

## 6. ¿Qué pasa cuando un shard se cae?

MongoDB utiliza conjuntos de réplicas, que permiten mantener copias de la información de cada shard.

Si falla uno de los servidores de un conjunto de réplicas, los demás pueden continuar trabajando y, si es necesario, elegir un nuevo servidor principal.

Pero si falla un shard completo y ninguna de sus réplicas está disponible, la información almacenada en él no se podrá consultar temporalmente. Los demás shards pueden seguir funcionando, aunque algunas consultas podrían fallar.

## Fuentes de consulta

- [MongoDB. Sharding](https://www.mongodb.com/docs/manual/sharding/)
- [MongoDB. Shard Keys](https://www.mongodb.com/docs/manual/core/sharding-shard-key/)
- [MongoDB. Config Servers](https://www.mongodb.com/docs/manual/core/sharded-cluster-config-servers/)
- [MongoDB. Add Shards to a Cluster](https://www.mongodb.com/docs/manual/tutorial/add-shards-to-shard-cluster/)
- [MongoDB. Troubleshoot Sharded Clusters](https://www.mongodb.com/es/docs/manual/tutorial/troubleshoot-sharded-clusters/)
