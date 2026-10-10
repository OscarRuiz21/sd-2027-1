# Tarea 3

**Alumno:** Erick Nava Santiago  
**Producto:** MongoDB  

---

## ¿Qué hace y para qué se usa?
Es una base de datos NoSQL orientada a documentos. Mongo permite distribuir colecciones en varias partes entre diferentes servidores, a estos se les llama *shards*. Sirve para escalar horizontalmente y ahorrar recursos que exige el escalamiento vertical.

## ¿Cómo reparte los datos?
La aplicación se conecta a los `mongos` que vendría siendo el router, este conecta con la configuración inicial y a su vez con las llaves de accesos conecta con los *shards*.

Las llaves de acceso las decide el DBA con el comando `sh.shardCollection(...)`, a esta llave se le llama *shard key* y puede ser compuesta.

MongoDB puede dividir tanto por *hash* como por rango, siendo que en la primera calcula un *hash* del valor de la llave y divide el espacio de *hashes* en rangos. En la segunda divide los valores de la llave en rangos contiguos. En ambos casos la unidad de reparto es el *chunk*.

## ¿Cómo encuentra el shard correcto?
Lo hace a partir del mapeo en la configuración del servidor. Al momento de localizar puede ejecutar una operación dirigida la cual incluye la llave de partición y MongoDB ubica el *chunk* y manda al *shard*; la otra es una operación por *broadcast* que se hace cuando no se tiene la llave de partición, haciendo que MongoDB tenga que preguntar en todos los *shards* si se encuentra el dato requerido.

## ¿Qué pasa cuando se agrega un shard?
Se crea un shard con el comando `sh.addShard(...)`. Al momento de crear un shard se crea vacío. Pronto, el *balancer* de Mongo se encarga de mover *chunks* completos en base a una proporción $1/N$ de los datos que se tienen. Los datos que se mueven siempre se procura que queden por debajo del umbral predeterminado.

## ¿Qué hace con un *shard* caliente?
Un *shard* caliente es cuando un *shard* recibe una carga de trabajo exagerada ocasionando problemas de rendimiento. Esto es un problema de tráfico, el cual no lo resuelve Mongo por defecto. Por parte del DBA se debe de gestionar y/o cambiar el estado cambiando cómo se están almacenando los datos o cambiando a un *hashed sharding* (el cual permite ubicar de forma pseudoaleatoria los *chunks*) o por medio de una llave de fragmentación compuesta, donde se combina un campo de alta cardinalidad y prefijo de distribución con el campo monótono.

---

## Referencias
1. [Database Manual V7.0 - MongoDB Docs. (s. f.). ](https://www.mongodb.com/docs/v7.0/reference/method/sh.shardCollection)
2. [Hashed Sharding - Database Manual - MongoDB Docs. (s. f.).](https://www.mongodb.com/docs/current/core/hashed-sharding)
3. [Routing with mongos - Database Manual - MongoDB Docs. (s. f.). ](https://www.mongodb.com/docs/manual/core/sharded-cluster-query-router/)
4. [Sharding - Database Manual - MongoDB Docs. (s. f.). ](https://www.mongodb.com/docs/manual/core/sharding-introduction/)
5. [Song, H. (2025). Customized openshift operator: An introduction to a tool for MongoDB automation and its application. En Osuva (University of Vaasa). ](https://osuva.uwasa.fi/handle/11111/19268)
