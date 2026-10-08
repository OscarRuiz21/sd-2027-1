# T03 · Una implementación real de sharding

**Alumno: Echeverria Goicochea Erick Isaac**

## MongoDB

### 1. Qué es y para qué se usa.

MongoDB es un sistema de gestión de bases de datos (DBMS) no relacional de código abierto que emplea documentos flexibles en lugar de tablas y filas para procesar y almacenar diversas formas de datos.

Se usa cuando necesitas manejar grandes volúmenes de datos y un tráfico elevado que una sola máquina ya no puede procesar. Su objetivo principal con el sharding es permitir el escalamiento horizontal: en lugar de comprar un servidor gigante y costoso (escalamiento vertical), reparte la carga de lecturas, escrituras y almacenamiento entre múltiples servidores más pequeños trabajando en equipo.


### 2. Cómo reparte los datos: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?

MongoDB permite repartir los datos de dos formas principales:

* Por Rango (Ranged Sharding): Agrupa los documentos contiguos según el valor de la llave (por ejemplo, nombres de la A a la F en un shard, de la G a la P en otro). Es ideal para hacer consultas por rangos de valores, pero si se insertan muchos datos seguidos (como fechas o IDs auto-incrementables), todos van a parar al mismo shard.

* Por Hash (Hashed Sharding): Aplica una función hash al valor de la llave para convertirlo en un número aleatorio y distribuirlo uniformemente entre los shards. Es la mejor opción para evitar cuellos de botella al escribir datos nuevos, aunque pierde eficiencia en consultas por rango.

La llave la elige el desarrollador o administrador de la base de datos al momento de configurar la colección. Es una decisión importante, ya que una buena shard key debe tener alta cardinalidad (muchos valores distintos) y distribuirse bien.

### 3. Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?

MongoDB usa una arquitectura dividida en dos componentes clave para el ruteo:

* Config Servers (Servidores de Configuración): Son los que guardan el mapa definitivo. Almacenan los metadatos del clúster y el registro exacto de qué rango de datos (chunks) vive en qué shard.

* Mongos (El Enrutador): Es el proceso intermedio que recibe las consultas de las aplicaciones. Al arrancar, mongos le pide una copia del mapa a los Config Servers y la guarda en su memoria caché.

Cuando llega una consulta, el cliente se conecta a mongos. Este revisa la shard key de la petición, consulta su mapa interno y redirige la consulta únicamente al shard donde vive ese dato. Si la consulta no incluye la shard key, mongos no sabe a dónde ir y tiene que enviarla a todos los shards (scatter-gather), lo cual es más lento.


### 4. Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?

Cuando se agrega un nuevo shard a un clúster de MongoDB, los datos se redistribuyen automáticamente para lograr un mejor equilibrio entre los nodos. MongoDB organiza la información en bloques llamados chunks, y cuando el nuevo shard se incorpora y está vacío, el proceso interno conocido como Balancer detecta que la distribución de los datos no es uniforme y comienza a mover información hacia él.

Sin embargo, no se mueve toda la base de datos. El Balancer únicamente migra la cantidad de chunks necesaria para equilibrar la carga entre los diferentes shards. Este proceso se realiza en segundo plano, trasladando los chunks desde los shards que tienen mayor cantidad de datos hacia el nuevo shard.
Además, la redistribución no requiere detener el servicio, ya que MongoDB puede continuar atendiendo operaciones de lectura y escritura mientras los datos se están moviendo. Una vez que un chunk ha sido trasladado correctamente, se actualiza la información correspondiente en los Config Servers para mantener el mapa de distribución del clúster.


### 5.Qué hace con un shard caliente, si hace algo.

Un shard caliente(hotspot), ocurre cuando un solo nodo recibe una cantidad mucho mayor de lecturas o escrituras que los demás, generalmente porque muchos datos comparten la misma clave de particionamiento o se concentran dentro de un mismo rango.

Cuando esto sucede, MongoDB puede dividir un chunk que ha crecido demasiado. Si el chunk supera el tamaño máximo configurado, que por defecto es de 64 MB, MongoDB realiza un proceso llamado chunk splitting, mediante el cual divide ese chunk en partes más pequeñas. Después, el Balancer puede intervenir y mover alguno de esos nuevos chunks hacia otro shard que tenga una menor carga, ayudando así a distribuir mejor los datos y las operaciones entre los nodos.

### Opcional: Levantar MongoDB

```yaml
# 1. Archivo docker-compose.yml
# Aqui se define la infraestructura necesaria: 1 Config Server, 2 Shards y 1 Router (mongos)
cat << 'EOF' > docker-compose.yml
services:
  configsvr:
    image: mongo:7.0
    container_name: configsvr
    command: mongod --configsvr --replSet configRS --port 27019 --bind_ip_all

  shard1:
    image: mongo:7.0
    container_name: shard1
    command: mongod --shardsvr --replSet shard1RS --port 27018 --bind_ip_all

  shard2:
    image: mongo:7.0
    container_name: shard2
    command: mongod --shardsvr --replSet shard2RS --port 27020 --bind_ip_all

  mongos:
    image: mongo:7.0
    container_name: mongos
    command: mongos --configdb configRS/configsvr:27019 --bind_ip_all --port 27017
    ports:
      - "27017:27017"
    depends_on:
      - configsvr
      - shard1
      - shard2
EOF

# 2. Levantar los contenedores
docker compose up -d

# 3. Inicializar los Replica Sets independientes en el Config Server y en cada Shard
docker exec configsvr mongosh --port 27019 --eval 'rs.initiate({_id: "configRS", configsvr: true, members: [{_id: 0, host: "configsvr:27019"}]})'
docker exec shard1 mongosh --port 27018 --eval 'rs.initiate({_id: "shard1RS", members: [{_id: 0, host: "shard1:27018"}]})'
docker exec shard2 mongosh --port 27020 --eval 'rs.initiate({_id: "shard2RS", members: [{_id: 0, host: "shard2:27020"}]})'

# 4. Registrar los dos Shards dentro del enrutador mongos
docker exec mongos mongosh --port 27017 --eval 'sh.addShard("shard1RS/shard1:27018"); sh.addShard("shard2RS/shard2:27020");'

# 5. Habilitar Sharding en la base de datos, crear un índice Hash e insertar datos de prueba
docker exec mongos mongosh --port 27017 --eval '
  sh.enableSharding("mi_base_datos");
  db.getSiblingDB("mi_base_datos").usuarios.createIndex({ usuario_id: "hashed" });
  sh.shardCollection("mi_base_datos.usuarios", { usuario_id: "hashed" });
  db.getSiblingDB("mi_base_datos").usuarios.insertMany([
    { usuario_id: 1, nombre: "Erick" },
    { usuario_id: 2, nombre: "Ana" },
    { usuario_id: 3, nombre: "Carlos" },
    { usuario_id: 4, nombre: "Sofía" },
    { usuario_id: 5, nombre: "Luis" }
  ]);
'

# 6. Consultar la distribución de los documentos entre los distintos shards
docker exec mongos mongosh --port 27017 --eval 'db.getSiblingDB("mi_base_datos").usuarios.getShardDistribution()'

```
### Salida

```bash
Shard shard1RS at shard1RS/shard1:27018
{
 data: '222B',
 docs: 4,
 chunks: 2,
 'estimated data per chunk': '111B',
 'estimated docs per chunk': 2
}
---
Shard shard2RS at shard2RS/shard2:27020
{
 data: '57B',
 docs: 1,
 chunks: 2,
 'estimated data per chunk': '28B',
 'estimated docs per chunk': 0
}
---
Totals
{
 data: '279B',
 docs: 5,
 chunks: 4,
 'Shard shard1RS': [
   '79.56 % data',
   '80 % docs in cluster',
   '55B avg obj size on shard'
 ],
 'Shard shard2RS': [
   '20.43 % data',
   '20 % docs in cluster',
   '57B avg obj size on shard'
 ]
}

```



## Bibliografia

MongoDB. (s. f.). *Sharding architecture and key concepts*. MongoDB Documentation. [https://www.mongodb.com/docs/manual/sharding/](https://www.mongodb.com/docs/manual/sharding/)

MongoDB. (s. f.). *Data partitioning with chunks and balancer process*. MongoDB Documentation. [https://www.mongodb.com/docs/manual/core/sharding-data-partitioning/](https://www.mongodb.com/docs/manual/core/sharding-data-partitioning/)

¿Qué es MongoDB? (2026, June 1). IBM. [https://www.ibm.com/mx-es/think/topics/mongodb](https://www.ibm.com/mx-es/think/topics/mongodb)

GeeksforGeeks. (2025, October 11). *Setting up a MongoDB sharded cluster*. GeeksforGeeks. [https://www.geeksforgeeks.org/mongodb/setting-up-a-mongodb-sharded-cluster/](https://www.geeksforgeeks.org/mongodb/setting-up-a-mongodb-sharded-cluster/)

Yasasvi. (2023, October 7). *MongoDB Sharding with Docker*. Medium. [https://medium.com/@yasasvi/mongodb-sharding-with-docker-c8b18bee32eb
](https://medium.com/@yasasvi/mongodb-sharding-with-docker-c8b18bee32eb)
