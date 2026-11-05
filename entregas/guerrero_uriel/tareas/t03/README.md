# T03: Una implementación de sharding en MongoDB

## 1. ¿Qué es MongoDB y para qué se usa?

MongoDB es una base de datos NoSQL que almacena la información en documentos, organizados en colecciones. Se utiliza para aplicaciones que manejan grandes cantidades de datos y necesitan flexibilidad para almacenar diferentes estructuras de información.

MongoDB permite utilizar sharding para distribuir una colección entre varios servidores. Esto ayuda a repartir el almacenamiento y la carga de trabajo cuando una sola máquina ya no es suficiente.

## 2. ¿Cómo reparte los datos?

MongoDB utiliza una clave de partición (*shard key*) para decidir cómo distribuir los documentos. Puede hacerlo por rango o mediante hash.

- Por rango: los documentos se distribuyen según los valores de la clave. Por ejemplo, los usuarios con identificadores del 1 al 100 podrían quedar en una partición y los del 101 al 200 en otra.
- Por hash: MongoDB calcula un valor hash de la clave y utiliza ese resultado para distribuir los documentos entre los shards.

La persona que diseña la base de datos elige la shard key al configurar el sharding. Es importante escogerla correctamente, porque una mala elección puede concentrar demasiadas operaciones en una sola partición.

## 3. ¿Cómo encuentra el shard correcto?

MongoDB utiliza un componente llamado *mongos*, que funciona como enrutador de consultas. La aplicación se conecta a este componente y no necesita conocer directamente todos los shards.

Los servidores de configuración (*config servers*) almacenan los metadatos que indican cómo se distribuyen los datos y qué rangos o particiones corresponden a cada shard.

Cuando llega una consulta, mongos consulta esa información y la dirige a los shards correspondientes. Si la consulta no incluye suficiente información sobre la shard key, puede necesitar consultar varios shards.

## 4. ¿Qué pasa cuando agregas un shard?

Cuando se agrega un shard al clúster, MongoDB puede redistribuir los datos para equilibrar la carga. Este proceso se realiza mediante el balanceador, que mueve fragmentos de datos (*chunks*) entre los shards.

No necesariamente se mueven todos los datos. Se trasladan los fragmentos que hacen falta para conseguir una distribución más equilibrada. El proceso puede consumir recursos de red, disco y CPU mientras se realiza.

## 5. ¿Qué hace con un shard caliente?

Un shard caliente es una partición que recibe muchas más consultas o escrituras que las demás. Esto puede convertirse en un cuello de botella aunque el resto de los servidores tenga recursos disponibles.

MongoDB puede redistribuir los chunks entre los shards para equilibrar la distribución de los datos. Sin embargo, esto no siempre resuelve el problema: si la mayoría de las operaciones utiliza un mismo valor de la shard key, esa clave puede seguir concentrando la carga.

Por eso, es importante elegir una shard key que distribuya adecuadamente las operaciones y evitar que todos los documentos o consultas importantes terminen concentrados en una misma partición.

## Fuentes consultadas

1. MongoDB. (s. f.). *Sharding*. MongoDB Manual. https://www.mongodb.com/docs/manual/sharding/

2. MongoDB. (s. f.). *Sharding methods*. MongoDB Manual. https://www.mongodb.com/docs/manual/sharding/#sharding-methods

3. MongoDB. (s. f.). *Sharded cluster components*. MongoDB Manual. https://www.mongodb.com/docs/manual/core/sharded-cluster-components/

4. MongoDB. (s. f.). *Sharded cluster balancer*. MongoDB Manual. https://www.mongodb.com/docs/manual/core/sharding-balancer-administration/

