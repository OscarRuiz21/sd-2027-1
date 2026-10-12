# Sharding en PostgreSQL con Citus

El procesamiento de volúmenes masivos de datos y la concurrencia representan desafíos para las bases de datos relacionales tradicionales. Para superar las limitaciones físicas de un servidor único, surgen tecnologías de fragmentación horizontal o sharding. Citus es una extensión de código abierto para PostgreSQL que transforma una instancia independiente en un sistema de base de datos distribuido altamente escalable. 



## 1. ¿Qué es y para qué se usa?
Citus es una extensión nativa de PostgreSQL que permite transformar un servidor PostgreSQL normal en una base de datos distribuida, manteniendo la compatibilidad con las herramientas y el ecosistema de Postgres.

En su nivel arquitectónico más básico, Citus divide una tabla extensa en múltiples tablas más pequeñas denominadas fragmentos o shards, las cuales se distribuyen físicamente a través de una red de servidores conocidos como nodos de trabajo o workers. El clúster es orquestado por un nodo centralizado llamado coordinador, cuyo propósito no es almacenar los datos operativos, sino planificar y enrutar las consultas provenientes de la aplicación hacia los nodos de trabajo correspondientes.

Esta capacidad de procesamiento paralelo masivo hace que la tecnología sea ideal para ecosistemas específicos.


*   **Aplicaciones multi-inquilino (SaaS B2B):** Aislamiento lógico de datos por cliente (tenant), permitiendo escalar a miles de clientes sin que la base de datos colapse.
*   **Analítica en tiempo real (HTAP):** Procesamiento de grandes volúmenes de datos (series temporales, eventos) donde las consultas se paralelizan en múltiples servidores.


## 2. ¿Cómo reparte los datos?
Citus utiliza principalmente el sharding por Hash (aunque también soporta sharding por anexión, o append, para casos muy específicos). Cuando se inserta un dato, Citus toma el valor de una columna específica, le aplica una función criptográfica (Hash) y el resultado determina a qué shard (fragmento lógico) pertenece. 

**¿Quién elige la llave?** 
La llave (conocida como Distribution Column o Columna de Distribución) la elige el desarrollador o arquitecto de la base de datos. Al configurar Citus, se debe ejecutar un comando (ej. `SELECT create_distributed_table('ventas', 'id_tienda');`) donde se decide explícitamente qué columna se usará para repartir la información. Elegir bien esta llave es el paso más crítico al usar Citus, ya que si la columna elegida posee baja cardinalidad (pocos valores distintos), el volumen total del sistema quedará restringido a unos pocos fragmentos, creando cuellos de botella y anulando el propósito de la arquitectura distribuida. Por lo tanto, las mejores prácticas estipulan elegir columnas de alta cardinalidad, con una distribución uniforme de datos, que sean frecuentemente utilizadas como filtros en cláusulas WHERE o en condiciones de cruce (JOIN).



## 3. ¿Cómo encuentra el shard correcto al consultar?

**Arquitectura de Coordinador y Trabajadores:** Citus divide los servidores en dos roles: un Nodo Coordinador (Coordinator Node) y varios Nodos Trabajadores (Worker Nodes).

**¿Quién guarda el mapa?** Cuando una aplicación emite una consulta SQL hacia el clúster, desconoce por completo la topología subyacente. El descubrimiento del shard correcto es responsabilidad exclusiva del nodo coordinador, quien almacena los metadatos (el mapa). Contiene tablas internas que registran exactamente qué rango de hashes corresponde a qué shard, y en qué Nodo Trabajador físico vive ese shard.

**Proceso de consulta** 
1. La aplicación envía la consulta SQL (ej. `SELECT * FROM ventas WHERE id_tienda = 5`) al Coordinador.
2. El Coordinador lee el `id_tienda = 5`, calcula su hash y revisa su "mapa de metadatos".
3. Descubre en qué trabajador está ese dato, reenvía la consulta directamente a ese servidor, recoge la respuesta y se la devuelve a la aplicación. Si la consulta no tiene la llave por ejemplo se solicita una consulta de todas las tiendas, el Coordinador envía la consulta a todos los trabajadores a la vez, junta los resultados y los entrega (consulta paralelizada).

## 4. ¿Qué pasa cuando se agrega un nuevo nodo/shard?
En Citus, una tabla suele dividirse en un número fijo de shards lógicos desde el principio (por defecto 32), los cuales se reparten entre los nodos físicos disponibles.

**¿Se mueven los datos automáticamente?** 
No, al agregar un nuevo servidor (nodo trabajador) al clúster de Citus, este se queda vacío inicialmente, simplemente se añade a los metadatos del Coordinador.

**Rebalanceo:** Para aprovechar el nuevo servidor, el administrador debe ejecutar explícitamente el Citus Shard Rebalancer (mediante la función `citus_rebalance_start()`).

**¿Cuántos datos se mueven?** El rebalanceador calcula el peso y mueve shards completos (no filas individuales) desde los nodos antiguos hacia el nodo nuevo, hasta que la carga o tamaño de los datos quede distribuido equitativamente en todo el clúster. Durante esta mudanza, la base de datos sigue funcionando y bloquea los datos de escritura solo una fracción de segundo al final de la transferencia.

---

### 5. ¿Qué hace con un shard caliente (Hot Shard)?
Un hot shard ocurre cuando un fragmento de datos recibe una cantidad desproporcionada de tráfico por parte de un cliente en comparación con clientes más pequeños que comparten el mismo servidor físico.

**¿Qué hace Citus?** Por defecto, no hace nada automáticamente para mitigar un shard caliente. No auto-fragmenta en caliente.

**La solución:** El administrador debe intervenir. Citus ofrece una herramienta específica llamada Aislamiento de Inquilino (Tenant Isolation). Usando la función `isolate_tenant_to_new_shard()`, el desarrollador puede extraer específicamente a ese "cliente gigante" de su shard actual y moverlo a un nuevo shard exclusivo, el cual puede ser colocado en un Nodo Trabajador dedicado que tenga más CPU y memoria solo para él.

## Referencias Bibliográficas
1. Citus Documentation - Citus 13.0.1 documentation. [Citus Architecture - docs.citusdata.com](https://docs.citusdata.com/en/v13.0/) 

2. Citus documentation - Distributed PostgreSQL. Microsoft Learn. [Citus documentation - Microsoft Learn](https://learn.microsoft.com/en-us/postgresql/citus/?view=citus-14)

3. Understanding partitioning and sharding in Postgres and Citus. (2023, 4 agosto). Citus Data. [citusdata.com/blog](https://www.citusdata.com/blog/2023/08/04/understanding-partitioning-and-sharding-in-postgres-and-citus/)
