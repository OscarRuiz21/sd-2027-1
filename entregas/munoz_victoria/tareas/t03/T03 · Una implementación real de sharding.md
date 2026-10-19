# T03 · Una implementación real de Sharding — MongoDB

### 1\. ¿Qué es MongoDB y para qué se usa el sharding?

MongoDB es una base de datos que guarda la información en documentos. 

El *sharding* se utiliza cuando hay demasiados datos o usuarios para que un solo servidor pueda manejarlos cómodamente. Lo que hace es repartir la información entre varios servidores, llamados *shards*, para que el almacenamiento y el trabajo se distribuyan entre ellos.

### 2\. ¿Cómo reparte los datos: por rango, por hash o de otra forma? ¿Quién elige la llave?

MongoDB puede repartir los datos principalmente por rango o utilizando un hash. Para hacerlo se elige una **shard key**, que es un campo de los documentos que sirve como referencia para decidir dónde guardar cada dato. Esta llave la elige el desarrollador o administrador de la base de datos, por lo que es importante escoger una que permita repartir los datos de manera equilibrada.

### 3\. ¿Cómo encuentra el shard correcto cuando llega una consulta?

MongoDB utiliza un componente llamado **mongos**, que funciona como intermediario entre la aplicación y los servidores. `mongos` consulta la información que tienen los **config servers**, donde se guarda el registro de qué parte de los datos está en cada shard. Con esta información puede enviar la consulta al servidor correspondiente. Si no puede saber dónde está el dato, puede consultar varios shards y juntar los resultados.

### 4\. ¿Qué pasa cuando agregas un shard? ¿Se mueven los datos?

Cuando se agrega un nuevo shard, MongoDB intenta repartir mejor los datos entre todos los servidores. Para hacerlo tiene un proceso llamado **balancer**, que puede mover algunas partes de los datos entre los shards. No se mueve toda la información, solamente los datos necesarios para conseguir una distribución más equilibrada.

### 5\. ¿Qué hace MongoDB con un shard caliente?

Un shard caliente es un servidor que está recibiendo mucha más información o consultas que los demás. MongoDB puede intentar equilibrar la carga moviendo algunas partes de los datos hacia otros shards. Sin embargo, si el problema se debe a una mala elección de la *shard key*, el balanceo por sí solo puede no ser suficiente. Por eso es importante elegir correctamente la llave desde el principio.

