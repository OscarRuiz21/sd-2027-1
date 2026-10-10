# T03 · Una implementación real de sharding

**Tecnología elegida:** MongoDB

**1. ¿Qué es y para qué se usa?**
El sharding es una técnica de partición horizontal de datos, se usa para escalar bases de datos cuando el volumen de datos o la carga de trabajo superan la capacidad de un solo servidor, consiste en dividir los datos en fragmentos (shards) y distribuirlos en múltiples servidores.

**2. ¿Cómo reparte los datos? ¿Quién elige la llave?**
MongoDB permite dos estrategias: Sharding por rango (Range-based) y Sharding por hash (Hashed); en ambos casos, el desarrollador elige la Shard Key (llave de partición) al momento de definir la colección, MongoDB usa esa llave para calcular a qué shard enviar cada documento.

**3. ¿Cómo encuentra el shard correcto?**
MongoDB utiliza un componente llamado mongos (router), cuando llega una consulta, el mongos consulta a los Config Servers, estos servidores guardan un mapa de metadatos que indica qué rangos de la Shard Key viven en qué shard, el mongos usa ese mapa para enrutar la consulta directamente al shard correcto.

**4. ¿Qué pasa cuando agregas un shard?**
Cuando agregas un nuevo shard, MongoDB activa un Balancer (balanceador), este divide los datos en unidades llamadas chunks y migra algunos de estos chunks desde los shards más cargados hacia el nuevo shard, no se mueven todos los datos, solo una fracción para equilibrar la carga.

**5. ¿Qué hace con un shard caliente?**
Un shard caliente ocurre cuando una Shard Key tiene baja cardinalidad o está muy sesgada, MongoDB no puede solucionarlo mágicamente; la solución es elegir una mejor Shard Key (con alta cardinalidad y baja frecuencia) o usar Hashed Sharding para distribuir la carga uniformemente.

**Fuentes:**

* Documentación oficial de MongoDB (Sharding).

