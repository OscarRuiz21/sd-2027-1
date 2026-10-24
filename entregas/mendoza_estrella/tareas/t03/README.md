# T03 — Sharding en MongoDB
**Alumna: Mendoza Camacho, Estrella de Maria**

## ¿Qué es y para qué se usa?

La tecnología seleccionada es MongoDB una base de datos no relacional orientada a documentos que permite distribuir información entre varios servidores mediante sharding.

El sharding consiste en dividir horizontalmente una colección de datos. En lugar de almacenar todos los documentos en un solo servidor, estos se distribuyen entre diferentes particiones llamadas shards.

## ¿Cómo reparte los datos?

Utiliza una shard key que es el campo que determina como se distribuyen los documentos.

Un ejemplo de ello es que en una colección de cuentas bancarias podríamos utilizar el campo clienteId como clave de partición.

Una estrategia sería utilizar hashed sharding, donde MongoDB calcula un hash del valor de clienteId y utiliza ese resultado para distribuir los documentos.

Esto ayuda a evitar que todas las cuentas nuevas se almacenen en un mismo shard.

## Cómo encuentra el shard correcto cuando llega una consulta?

MongoDB utiliza un componente llamado mongos, encargado de recibir las solicitudes de la aplicación y dirigirlas a los shards correspondientes.

Los servidores de configuración mantienen los metadatos necesarios para conocer la distribución de los datos.

Si una consulta incluye la shard key, mongos puede identificar a que shard enviarla. Si no la incluye, podría necesitar consultar varios shards, lo que puede aumentar el trabajo del sistema.

## ¿Qué pasa cuando agregas un shard?

Cuando se agrega un shard, MongoDB puede redistribuir parte de los datos para equilibrar el almacenamiento.

Para realizarlo utiliza un proceso llamado balancer, que mueve rangos de datos entre los shards cuando resulta necesario.

Esto permite aumentar la capacidad del sistema sin trasladar manualmente todos los documentos. Sin embargo, la redistribución puede generar consumo adicional de recursos mientras se realiza.

##  ¿Qué hace con un shard caliente?

Un hot shard es una partición que recibe una cantidad de operaciones mucho mayor que las demás.

Por ejemplo, si todas las escrituras se concentran en un rango de identificadores consecutivos, un solo shard podría recibir la mayoría de las solicitudes.

Una forma de reducir este problema es elegir una shard key con buena distribución y, cuando corresponde, utilizar hashed sharding.


## Referencias

MongoDB. (s. f.). Claves de partición. https://www.mongodb.com/docs/manual/core/sharding-shard-key/

MongoDB. (s. f.). Componentes de un clúster particionado. https://www.mongodb.com/docs/v7.0/core/sharded-cluster-components/

MongoDB. (s. f.). Particionado. https://www.mongodb.com/docs/v8.0/sharding/

MongoDB. (s. f.). Solucionar problemas de claves de fragmentos. https://www.mongodb.com/docs/manual/core/sharding-troubleshooting-shard-keys/