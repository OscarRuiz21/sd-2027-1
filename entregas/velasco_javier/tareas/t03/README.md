# T03 · Una implementación real de sharding


Sistemas Distribuidos

**Alumno:** Javier Velasco Pacheco

## Introducción

En clase vimos que el sharding permite dividir los datos de una base de datos entre varios servidores para distribuir el almacenamiento y el trabajo. 

**Tecnología elegida:** MongoDB

Para este trabajo elegí MongoDB, una tecnología que permite implementar sharding en colecciones de documentos. 

## 1. ¿Qué es MongoDB y para qué se usa?

MongoDB es una base de datos NoSQL que guarda la información en documentos, en lugar de usar tablas y filas como otras bases de datos. Se utiliza en páginas web, tiendas en línea y aplicaciones que necesitan guardar y consultar mucha información.

Cuando una aplicación crece, una sola computadora puede no ser suficiente para guardar todos los datos o atender tantas consultas. Para resolver esto, MongoDB permite usar sharding, que consiste en repartir los datos entre varios servidores. Cada servidor guarda una parte de la información y, si se necesita más capacidad, se pueden agregar otros servidores sin depender de una sola máquina.

## 2. ¿Cómo reparte los datos y quién elige la llave?

MongoDB utiliza una shard key para decidir cómo repartir los datos entre los servidores. Esta llave es un campo de los documentos que elige la persona que configura la base de datos.

MongoDB puede repartir los datos de dos formas:

### a) Por rango

Agrupa los datos según sus valores. Por ejemplo, un servidor puede  guardar los clientes con identificadores del 1 al 100 y otro los del 101 al 200. Esto ayuda cuando se buscan datos dentro de un rango, pero un servidor puede recibir más trabajo que los demás.

Este método puede resultar útil cuando se realizan consultas por intervalos, porque permite localizar los datos correspondientes a determinados valores. Si las operaciones se encuentra en un rango específico, uno de los shards puede recibir más trabajo que los demás.

![Repartición por Rango](https://www.mongodb.com/es/docs/docs_static_manual/_next/static/images/manual/manual/images/sharding-range-based.bakedsvg.svg)

### b) Por hash

Cuando MongoDB usa hash, primero calcula un valor a partir de la shard key. Con ese resultado, determina en qué parte de la distribución corresponde guardar el dato.

Por ejemplo, si los identificadores son 1, 2, 3, 4 y 5, MongoDB calcula un hash para cada uno. Como los resultados son diferentes y quedan distribuidos según esos valores, los datos pueden repartirse entre distintos shards en lugar de guardarse todos juntos por ser consecutivos.

![Repartición por Hash](https://www.mongodb.com/es/docs/docs_static_manual/_next/static/images/manual/manual/images/sharding-hash-based.bakedsvg.svg)

## 3. ¿Cómo encuentra MongoDB el shard correcto?

Cuando MongoDB reparte los datos entre varios servidores, cada servidor (shard) guarda una parte de la información. Para encontrar los datos, utiliza un componente llamado mongos, que funciona como intermediario entre la aplicación y los shards.

Cuando la aplicación busca un dato, mongos revisa la consulta y utiliza un mapa guardado en los servidores de configuración para saber en qué shard se encuentra la información.Si los datos están repartidos entre tres shards, mongos identifica a cuál debe enviar la consulta y después devuelve el resultado a la aplicación. Si no puede identificar el shard exacto, puede consultar varios.

![Arquitectura Shards](https://www.mongodb.com/es/docs/docs_static_manual/_next/static/images/manual/manual/images/sharded-cluster-production-architecture.bakedsvg.svg)

## 4. ¿Qué pasa cuando se agrega un shard?

Cuando se agrega un nuevo shard, MongoDB puede mover algunos fragmentos de datos de los servidores existentes al nuevo. Un proceso llamado balanceador ayuda a repartir los datos de manera más equilibrada. 

No se mueven todos los datos, sino solo los necesarios para mejorar la distribución. Este proceso puede tardar, por lo que agregar un servidor no significa que los datos se repartan de inmediato.

## 5. ¿Qué hace MongoDB con un shard caliente?

Un shard caliente ocurre cuando un servidor recibe muchas más consultas que los demás y puede volverse más lento.

Para solucionar este problema, MongoDB puede mover datos entre los servidores para mejorar su distribución. También se puede revisar la shard key y cambiarla si no ayuda a repartir bien las consultas. Otra opción es agregar más shards para aumentar la capacidad del sistema, aunque esto no siempre resuelve el problema.

Por eso, es importante revisar cómo se distribuyen los datos y cómo la aplicación realiza sus consultas, para evitar que todo el trabajo se concentre en un solo servidor.


## 6. Implementación con Docker Compose

### 6.1. Configuración del clúster

Se creó un archivo compose.yaml para ejecutar cuatro servicios:

* configsvr: servidor de configuración del clúster.
* shard1: primer shard, con conjunto de réplicas shard1RS`.
* shard2: segundo shard, con conjunto de réplicas shard2RS.
* mongos: enrutador que recibe las consultas y las dirige a los shards.


El compose.yaml es el siguiente: 
```bash
services:
  configsvr:
    image: mongo:7.0
    command: ["mongod", "--configsvr", "--replSet", "cfgRS", "--port", "27019", "--bind_ip_all"]
    volumes:
      - config_data:/data/configdb
    networks:
      - mongo_net

  shard1:
    image: mongo:7.0
    command: ["mongod", "--shardsvr", "--replSet", "shard1RS", "--port", "27018", "--bind_ip_all"]
    volumes:
      - shard1_data:/data/db
    networks:
      - mongo_net

  shard2:
    image: mongo:7.0
    command: ["mongod", "--shardsvr", "--replSet", "shard2RS", "--port", "27018", "--bind_ip_all"]
    volumes:
      - shard2_data:/data/db
    networks:
      - mongo_net

  mongos:
    image: mongo:7.0
    command: ["mongos", "--configdb", "cfgRS/configsvr:27019", "--bind_ip_all", "--port", "27017"]
    ports:
      - "27017:27017"
    depends_on:
      - configsvr
      - shard1
      - shard2
    networks:
      - mongo_net

volumes:
  config_data:
  shard1_data:
  shard2_data:

networks:
  mongo_net:
```

### 6.2. Iniciar los contenedores

Desde la carpeta D:\sharding-mongodb, se ejecutó:

```cmd
docker compose up -d
```

El comando descargó la imagen de MongoDB y creó los contenedores, la red y los volúmenes necesarios.

Después se comprobó su estado:

```cmd
docker compose ps
```

Los cuatro servicios aparecieron en estado Up.

```cmd
D:\sharding-mongodb>docker compose up -d
[+] up 19/19
 ✔ Image mongo:7.0                        Pulled                                                                  138.9s
 ✔ Volume sharding-mongodb_shard1_data    Created                                                                   0.0s
 ✔ Volume sharding-mongodb_config_data    Created                                                                   0.0s
 ✔ Network sharding-mongodb_mongo_net     Created                                                                   0.1s
 ✔ Volume sharding-mongodb_shard2_data    Created                                                                   0.0s
 ✔ Container sharding-mongodb-configsvr-1 Started                                                                   0.9s
 ✔ Container sharding-mongodb-shard2-1    Started                                                                   1.1s
 ✔ Container sharding-mongodb-shard1-1    Started                                                                   1.3s
 ✔ Container sharding-mongodb-mongos-1    Started                                                                   1.2s

D:\sharding-mongodb>docker compose ps
NAME                           IMAGE       COMMAND                  SERVICE     CREATED          STATUS          PORTS
sharding-mongodb-configsvr-1   mongo:7.0   "docker-entrypoint.s…"   configsvr   41 seconds ago   Up 40 seconds   27017/tcp
sharding-mongodb-mongos-1      mongo:7.0   "docker-entrypoint.s…"   mongos      41 seconds ago   Up 40 seconds   0.0.0.0:27017->27017/tcp, [::]:27017->27017/tcp
sharding-mongodb-shard1-1      mongo:7.0   "docker-entrypoint.s…"   shard1      41 seconds ago   Up 40 seconds   27017/tcp
sharding-mongodb-shard2-1      mongo:7.0   "docker-entrypoint.s…"   shard2      41 seconds ago   Up 40 seconds   27017/tcp
```

### 6.3. Inicializar los conjuntos de réplicas

Se inicializó el servidor de configuración con el siguiente comando:

```cmd
docker compose exec configsvr mongosh --port 27019 --eval "rs.initiate({_id:'cfgRS',configsvr:true,members:[{_id:0,host:'configsvr:27019'}]})"
```

Después se inicializó el primer shard:

```cmd
docker compose exec shard1 mongosh --port 27018 --eval "rs.initiate({_id:'shard1RS',members:[{_id:0,host:'shard1:27018'}]})"
```

Y finalmente, el segundo shard:

```cmd
docker compose exec shard2 mongosh --port 27018 --eval "rs.initiate({_id:'shard2RS',members:[{_id:0,host:'shard2:27018'}]})"
```

Los tres comandos devolvieron { ok: 1 }, indicando que las operaciones de inicialización fueron aceptadas.

Para comprobar el estado de cada conjunto de réplicas se utilizaron:

```cmd
docker compose exec configsvr mongosh --port 27019 --eval "rs.status().myState"
docker compose exec shard1 mongosh --port 27018 --eval "rs.status().myState"
docker compose exec shard2 mongosh --port 27018 --eval "rs.status().myState"
```



Los tres devolvieron 1, correspondiente al estado *PRIMARY*.

```cmd
D:\sharding-mongodb>docker compose exec configsvr mongosh --port 27019 --eval "rs.status().myState"
1

D:\sharding-mongodb>docker compose exec shard1 mongosh --port 27018 --eval "rs.status().myState"
1

D:\sharding-mongodb>docker compose exec shard2 mongosh --port 27018 --eval "rs.status().myState"
1
```

### 6.4. Registrar los shards en el clúster

Se accedió a la consola de mongos mediante:

```cmd
docker compose exec mongos mongosh --port 27017
```

Dentro de la consola de MongoDB se ejecutaron:

```javascript
sh.addShard("shard1RS/shard1:27018")
sh.addShard("shard2RS/shard2:27018")
```

Ambos comandos devolvieron ok: 1, y MongoDB confirmó que los dos shards fueron agregados.

Para comprobar el estado del clúster se ejecutó:

```javascript
sh.status()
```

El resultado mostró los shards shard1RS y shard2RS, ambos con estado 1.

```javascript
[direct: mongos] test> sh.status()
shardingVersion
{ _id: 1, clusterId: ObjectId('6ac9d39ab1935448c4d6675d') }
---
shards
[
  {
    _id: 'shard1RS',
    host: 'shard1RS/shard1:27018',
    state: 1,
    topologyTime: Timestamp({ t: 1791611869, i: 1 })
  },
  {
    _id: 'shard2RS',
    host: 'shard2RS/shard2:27018',
    state: 1,
    topologyTime: Timestamp({ t: 1791611876, i: 2 })
  }
]
---
active mongoses
[ { '7.0.43': 1 } ]
---
autosplit
{ 'Currently enabled': 'yes' }
---
balancer
{
  'Currently enabled': 'yes',
  'Currently running': 'no',
  'Failed balancer rounds in last 5 attempts': 0,
  'Migration Results for the last 24 hours': 'No recent migrations'
}
---
shardedDataDistribution
[]
---
databases
[
  {
    database: { _id: 'config', primary: 'config', partitioned: true },
    collections: {}
  }
]
```

### 6.5. Crear la base de datos y la colección distribuida

Dentro de la consola de mongos se habilitó el sharding para la base de datos t03:

```javascript
sh.enableSharding("t03")
```

Se seleccionó la base de datos y se creó la colección usuarios:

```javascript
use t03
db.createCollection("usuarios")
```

Después se configuró la colección para distribuir sus documentos mediante la clave id:

```javascript
sh.shardCollection("t03.usuarios", {id: 1})
```

La respuesta confirmó que la colección quedó configurada para sharding.

### 6.6. Dividir y mover un rango

Para establecer un límite en el valor 51 se ejecutó:

```javascript
sh.splitAt("t03.usuarios", {id: 51})
```

Este comando dividió el rango de datos en dos partes. Posteriormente, se movió el rango superior al segundo shard:

```javascript
sh.moveChunk("t03.usuarios", {id: 51}, "shard2RS")
```

La operación devolvió ok: 1, confirmando que el movimiento se realizó correctamente.

**[CAPTURA 5: comandos sh.shardCollection, sh.splitAt` y `sh.moveChunk` con sus resultados]**

### 6.7. Insertar documentos de prueba

Se insertaron diez documentos en la colección usuarios:

```javascript
db.usuarios.insertMany([
  {id: 1, nombre: "Ana"},
  {id: 2, nombre: "Luis"},
  {id: 3, nombre: "Sofia"},
  {id: 4, nombre: "Carlos"},
  {id: 5, nombre: "Maria"},
  {id: 51, nombre: "Pedro"},
  {id: 52, nombre: "Laura"},
  {id: 53, nombre: "Diego"},
  {id: 54, nombre: "Elena"},
  {id: 55, nombre: "Pablo"}
])
```

La respuesta indicó que la inserción fue aceptada y devolvió los identificadores generados para los diez documentos.

```javascript
 acknowledged: true,
  insertedIds: {
    '0': ObjectId('6ac9d45e0e81df518c322162'),
    '1': ObjectId('6ac9d45e0e81df518c322163'),
    '2': ObjectId('6ac9d45e0e81df518c322164'),
    '3': ObjectId('6ac9d45e0e81df518c322165'),
    '4': ObjectId('6ac9d45e0e81df518c322166'),
    '5': ObjectId('6ac9d45e0e81df518c322167'),
    '6': ObjectId('6ac9d45e0e81df518c322168'),
    '7': ObjectId('6ac9d45e0e81df518c322169'),
    '8': ObjectId('6ac9d45e0e81df518c32216a'),
    '9': ObjectId('6ac9d45e0e81df518c32216b')
  }
```

### 6.8. Comprobar la distribución de los documentos

Se ejecutó nuevamente:

```javascript
sh.status()
```

En la sección shardedDataDistribution, MongoDB reportó cinco documentos en shard1RS y cinco en shard2RS. En la sección de rangos de la colección también se observó que el límite de distribución se encuentra en id: 51.

```javascript
[direct: mongos] t03> sh.status()
shardingVersion
{ _id: 1, clusterId: ObjectId('6ac9d39ab1935448c4d6675d') }
---
shards
[
  {
    _id: 'shard1RS',
    host: 'shard1RS/shard1:27018',
    state: 1,
    topologyTime: Timestamp({ t: 1791611869, i: 1 })
  },
  {
    _id: 'shard2RS',
    host: 'shard2RS/shard2:27018',
    state: 1,
    topologyTime: Timestamp({ t: 1791611876, i: 2 })
  }
]
---
active mongoses
[ { '7.0.43': 1 } ]
---
autosplit
{ 'Currently enabled': 'yes' }
---
balancer
{
  'Currently enabled': 'yes',
  'Currently running': 'no',
  'Failed balancer rounds in last 5 attempts': 0,
  'Migration Results for the last 24 hours': { '1': 'Success' }
}
---
shardedDataDistribution
[
  {
    ns: 't03.usuarios',
    shards: [
      {
        shardName: 'shard2RS',
        numOrphanedDocs: 0,
        numOwnedDocuments: 5,
        ownedSizeBytes: 240,
        orphanedSizeBytes: 0
      },
      {
        shardName: 'shard1RS',
        numOrphanedDocs: 0,
        numOwnedDocuments: 5,
        ownedSizeBytes: 235,
        orphanedSizeBytes: 0
      }
    ]
  }
]
---
databases
[
  {
    database: { _id: 'config', primary: 'config', partitioned: true },
    collections: {}
  },
  {
    database: {
      _id: 't03',
      primary: 'shard1RS',
      partitioned: false,
      version: {
        uuid: UUID('c7a9cac2-f012-4f82-b319-d2134eaad5b0'),
        timestamp: Timestamp({ t: 1791611954, i: 1 }),
        lastMod: 1
      }
    },
    collections: {
      't03.usuarios': {
        shardKey: { id: 1 },
        unique: false,
        balancing: true,
        allowMigrations: true,
        chunkMetadata: [
          { shard: 'shard1RS', nChunks: 1 },
          { shard: 'shard2RS', nChunks: 1 }
        ],
        chunks: [
          { min: { id: MinKey() }, max: { id: 51 }, 'on shard': 'shard1RS', 'last modified': Timestamp({ t: 2, i: 1 }) },
          { min: { id: 51 }, max: { id: MaxKey() }, 'on shard': 'shard2RS', 'last modified': Timestamp({ t: 2, i: 0 }) }
        ],
        tags: []
      }
    }
  }
]
```

Para verificar directamente qué documentos almacena cada shard, se utilizaron las siguientes consultas desde CMD, fuera de la consola interactiva de mongosh.

Consulta del primer shard:

```cmd
docker compose exec shard1 mongosh --port 27018 --eval "db.getSiblingDB('t03').usuarios.find({}, {_id:0}).sort({id:1}).toArray()"
```

Consulta del segundo shard:

```cmd
docker compose exec shard2 mongosh --port 27018 --eval "db.getSiblingDB('t03').usuarios.find({}, {_id:0}).sort({id:1}).toArray()"
```
```cmd
D:\sharding-mongodb>docker compose exec shard1 mongosh --port 27018 --eval "db.getSiblingDB('t03').usuarios.find({}, {_id:0}).sort({id:1}).toArray()"
[
  { id: 1, nombre: 'Ana' },
  { id: 2, nombre: 'Luis' },
  { id: 3, nombre: 'Sofia' },
  { id: 4, nombre: 'Carlos' },
  { id: 5, nombre: 'Maria' }
]
```
```cmd
D:\sharding-mongodb>docker compose exec shard2 mongosh --port 27018 --eval "db.getSiblingDB('t03').usuarios.find({}, {_id:0}).sort({id:1}).toArray()"
[
  { id: 51, nombre: 'Pedro' },
  { id: 52, nombre: 'Laura' },
  { id: 53, nombre: 'Diego' },
  { id: 54, nombre: 'Elena' },
  { id: 55, nombre: 'Pablo' }
]
```

## 7. Resultados

La implementación permitió comprobar la distribución de los diez documentos entre dos shards:

| Shard      | Rango de id        | Documentos |
| ---------- | -------------------- | ---------: |
| shard1RS | Menores que 51       |          5 |
| shard2RS | Desde 51 en adelante |          5 |
| **Total**  |                      |     **10** |

La configuración de rangos y el resultado de sh.status() confirmaron que MongoDB reconoce los dos shards y que cada uno posee una parte de la colección.

## Conclusión

Al investigar e implementar MongoDB, comprendí cómo el sharding permite distribuir los datos entre varios servidores y la importancia de elegir correctamente la shard key. También entendí cómo mongos dirige las consultas y cómo el balanceador ayuda a distribuir los datos. Esta tarea me permitió relacionar los conceptos vistos en clase con una implementación real y reconocer que el rendimiento de un sistema distribuido depende de cómo se diseña y configura.

## Referencias

MongoDB. (s. f.). *Sharding*. MongoDB Manual. https://www.mongodb.com/docs/manual/sharding/

MongoDB. (s. f.). *Shard keys*. MongoDB Manual. https://www.mongodb.com/docs/manual/core/sharding-shard-key/

MongoDB. (s. f.). *Data partitioning with chunks*. MongoDB Manual. https://www.mongodb.com/docs/manual/core/sharding-data-partitioning/

MongoDB. (s. f.). *Sharded cluster components*. MongoDB Manual. https://www.mongodb.com/docs/manual/core/sharded-cluster-components/

---