# T03: Una implementación real de sharding con Citus

## 1. Qué es y para qué se usa
**Citus** es una extensión de código abierto para PostgreSQL que transforma una base de datos relacional monolítica en un sistema de base de datos distribuida. Se utiliza principalmente para escalar bases de datos sin tener que abandonar la semántica transaccional ni el ecosistema de herramientas de SQL. Es ideal para aplicaciones SaaS multi-inquilino (multi-tenant) y sistemas de analítica en tiempo real, donde el volumen de datos o la concurrencia sobrepasan la capacidad de cómputo y almacenamiento de un solo servidor.

## 2. Cómo reparte los datos
Citus distribuye los datos utilizando el método de partición por **Hash**. 
Es el desarrollador quien elige la **llave de partición** (conocida como *distribution column*). Al insertar un nuevo registro, Citus aplica una función hash criptográfica a esa columna en específico y, basándose en el resultado, asigna el dato a un *shard* lógico (un fragmento de la tabla). Estos shards lógicos son luego distribuidos y almacenados equitativamente entre los distintos nodos trabajadores disponibles en el clúster.

## 3. Cómo encuentra el shard correcto
La arquitectura de Citus funciona bajo el modelo de un **Nodo Coordinador** y múltiples **Nodos Trabajadores (Workers)**. 
El Coordinador es el único que almacena de manera centralizada las tablas de metadatos del clúster; es decir, guarda el mapa que indica qué *shard* lógico reside en qué nodo trabajador físico. Cuando llega una consulta SQL, esta entra al Coordinador; el Coordinador extrae el valor de la llave de partición presente en la cláusula `WHERE`, consulta sus metadatos internos, planifica la consulta y la enruta directamente al nodo trabajador específico que contiene esa información, devolviendo el resultado consolidado al cliente de forma transparente.

## 4. Qué pasa cuando agregas un shard nuevo
Si la base de datos se satura y se agrega un nuevo nodo trabajador físico al clúster, los datos **no se mueven ni se rebalancean automáticamente** al instante. El administrador de la base de datos debe invocar manualmente una función de rebalanceo (como `rebalance_table_shards()`). Al ejecutarla, el Coordinador transfiere de forma segura *shards* lógicos completos desde los nodos más congestionados hacia el nodo recién agregado, actualizando sus metadatos en tiempo real sin requerir tiempos de inactividad (downtime) en el sistema.

## 5. Qué hace con un shard caliente
En arquitecturas multi-inquilino, es común que un cliente de gran tamaño concentre demasiado tráfico y genere un "shard caliente" (hot shard) que degrade el rendimiento general. Para mitigar esto, Citus implementa un mecanismo llamado **Aislamiento de Inquilino (Tenant Isolation)**. Mediante la ejecución de la función `isolate_tenant_to_new_shard`, Citus permite extraer de manera quirúrgica todos los registros asociados a la llave de ese inquilino específico y los migra hacia un shard dedicado exclusivo. Este nuevo shard puede ser reubicado en un nodo trabajador físico completamente nuevo y con mayor capacidad de hardware, aislando así la carga de trabajo pesada y protegiendo el rendimiento del resto de los clientes en el sistema.

---

## Puntos Extra: Implementación con Docker

Se levantó un clúster local de Citus compuesto por un nodo coordinador y dos nodos trabajadores (`worker-1` y `worker-2`) utilizando Docker Compose. Se creó la tabla `usuarios` y se distribuyó lógicamente utilizando la columna `pais` como llave de partición. 

Tras insertar registros de prueba y consultar las tablas de metadatos internas de Citus (`pg_dist_shard_placement`), se obtuvo la siguiente salida que demuestra la fragmentación real y la distribución física de los datos a través del clúster:

```sql
postgres=# SELECT shardid, nodename
FROM pg_dist_shard_placement
LIMIT 20;
 shardid | nodename
---------+----------
  102008 | worker-1
  102010 | worker-1
  102012 | worker-1
  102014 | worker-1
  102016 | worker-1
  102018 | worker-1
  102020 | worker-1
  102022 | worker-1
  102024 | worker-1
  102026 | worker-1
  102028 | worker-1
  102030 | worker-1
  102032 | worker-1
  102034 | worker-1
  102036 | worker-1
  102038 | worker-1
  102009 | worker-2
  102011 | worker-2
  102013 | worker-2
  102015 | worker-2
(20 rows)
```

## Referencias
[1] Microsoft, "¿Qué es Citus? - Azure Database for PostgreSQL," Microsoft Learn, 15-abr-2024. [En línea]. Disponible: https://learn.microsoft.com/es-es/postgresql/citus/what-is-citus?view=citus-14. [Accedido: 09-oct-2026].

[2] Microsoft, "Single-node Docker - Azure Database for PostgreSQL," Microsoft Learn, 15-abr-2024. [En línea]. Disponible: https://learn.microsoft.com/en-us/postgresql/citus/single-node-docker?view=citus-14. [Accedido: 09-oct-2026].