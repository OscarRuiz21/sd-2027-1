# T03: Implementación de Sharding con Citus (PostgreSQL)

Para esta tarea decidí investigar sobre Citus, que básicamente es una extensión para PostgreSQL.

## 1. Qué es y para qué se usa

Citus no es un motor de base de datos nuevo desde cero, sino una extensión que le instalas a PostgreSQL para convertirlo en una base de datos distribuida. Se usa mucho cuando tu base ya creció demasiado (por ejemplo, en aplicaciones SaaS donde tienes muchísimos clientes) y necesitas escalar horizontalmente metiendo más servidores, pero quieres seguir usando el SQL de toda la vida sin romper tu código.

## 2. Cómo reparte los datos

Citus reparte los datos usando **sharding por hash**.

Aquí el chiste es que la base de datos no adivina cómo partir las cosas; uno como desarrollador es el que elige la llave de partición (a la que le llaman `Distribution Column`). O sea, cuando creas una tabla, tú le tienes que decir explícitamente "usa esta columna (tipo el `usuario_id` o `tenant_id`) para sacar el hash y repartir los datos".

## 3. Cómo encuentra el shard correcto

El sistema funciona con un nodo principal llamado **Coordinator** (coordinador) y varios nodos **Workers** (los trabajadores).

El Coordinator es el que guarda el mapa. Tiene unas tablas de metadatos que relacionan los rangos de hashes con los shards lógicos y en qué Worker físico están. Cuando tu aplicación hace una consulta, esta siempre entra por el Coordinator; él revisa su mapa, reescribe la consulta, se la manda a los Workers que tienen esos datos, junta los resultados y te los regresa. Tú ni te enteras de que hay varios servidores atrás.

## 4. Qué pasa cuando agregas un shard

Si conectas un nuevo nodo Worker al clúster, los datos no se mueven solos de forma automática.

Para que el nuevo nodo empiece a recibir datos, tienes que correr una función explícita que se llama `rebalance_table_shards()`. Lo que hace esto es analizar cómo está distribuido todo y empieza a mover solo los fragmentos (shards) necesarios desde los nodos más llenos hacia el nuevo para que todos queden parejos. Lo bueno es que hace este movimiento en segundo plano sin bloquear las lecturas ni escrituras.

## 5. Qué hace con un shard caliente

Si resulta que un cliente es muy "atascado" y genera un shard caliente que consume todos los recursos, Citus tiene una función especial para eso llamada **Tenant Isolation**.

Básicamente usas el comando `isolate_tenant_to_new_shard()` y Citus saca todos los datos específicos de ese cliente y los pasa a un shard dedicado. Ese shard luego lo puedes poner en un nodo Worker que tenga hardware más potente, para que ese cliente pesado no alente a los demás.

## Referencias
* Citus Data. (2025). Citus 13.0.1 documentation. Microsoft. citusdata.com
* Citus Data. (2026). Citus: Distributed PostgreSQL as an extension (Versión 14.1) [Repositorio de código fuente]. GitHub. github.com