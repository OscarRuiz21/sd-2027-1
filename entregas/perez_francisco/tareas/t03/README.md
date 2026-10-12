# T03 - Una implementación real de sharding

**Pérez Nava Francisco Javier**

**Tecnología elegida: PostgreSQL con Citus**

## 1. ¿Qué es y para qué se usa?

PostgreSQL es un sistema que permite guardar y organizar información en bases de datos y con ayuda de una extensión llamada Citus podemos utilizar sharding para repartir los datos entre varios servidores, esto es útil cuando tenemos demasiada información y queremos evitar que todo dependa de un solo servidor.

## 2. ¿Cómo reparte los datos?

Citus utiliza principalmente el método hash, que consiste en hacer un cálculo con el valor de una columna para decidir en qué shard guardar la información, esta columna la elegimos nosotros al configurar la tabla. Por ejemplo, si tenemos una base de datos de clientes podemos utilizar su número de identificación para repartirlos entre diferentes shards.

## 3. ¿Cómo encuentra el shard correcto?

Citus utiliza un servidor llamado coordinador, que se encarga de recibir las consultas y saber dónde se encuentra la información, para esto tiene un registro que indica en qué servidor está cada shard. Por ejemplo, si buscamos los datos de un cliente, el coordinador identifica dónde están guardados y manda la consulta al servidor correspondiente para obtener la información.

## 4. ¿Qué pasa cuando agregamos un shard?

Cuando agregamos un nuevo servidor, Citus permite mover algunos shards para que la información quede mejor repartida y no se concentre todo en un mismo lugar, no es necesario mover toda la base de datos, solamente los shards que hagan falta para equilibrar la distribución. La cantidad de datos que se mueve depende de cómo estén repartidos y del tamaño de cada shard.

## 5. ¿Qué hace con un shard caliente?

Un shard caliente ocurre cuando una parte de la base de datos recibe muchas más consultas que las demás y empieza a generar demasiado trabajo en un servidor, para ayudar con esto Citus permite mover shards entre servidores o separar los datos que generan mucha carga, aunque esto no siempre soluciona el problema por completo y por eso es importante elegir bien la columna que se utiliza para repartir la información.

## Fuentes consultadas

1. CitusData. (s. f.). *Concepts - citus*. Recuperado 11 de octubre de 2026, de [https://docs.citusdata.com/en/stable/get_started/concepts.html](https://docs.citusdata.com/en/stable/get_started/concepts.html)

2. Microsoft. (s. f.). *Create and modify distributed objects*. Recuperado 11 de octubre de 2026, de [https://learn.microsoft.com/en-us/postgresql/citus/reference-ddl](https://learn.microsoft.com/en-us/postgresql/citus/reference-ddl)

3. Microsoft. (s. f.). *Manage your Citus cluster*. Recuperado 11 de octubre de 2026, de [https://learn.microsoft.com/en-us/postgresql/citus/cluster-management](https://learn.microsoft.com/en-us/postgresql/citus/cluster-management)
