T03 · Una implementación real de sharding

Asignada en la S7 (3-oct) · entrega el sábado 10 de octubre, antes de las 07:00

Alumno: Fernández Herrera Mauricio - sistemas distribuidos - Grupo 02



1.- MongoDB es un manejador de bases de datos de tipo NoSQL. Este utiliza el sharding para distribuir una colección de datos entre varios servidores, de modo que se reduzca la sobrecarga y posibles cuellos de botella al repartir la carga entre varios nodos.



2.- Mongo DB reparte sus datos tanto por rango como por hash. El primero divide los valores de la shard key en rangos, los cuales después se organizan en chunks, siendo estos un rango de valores que adopta nuestra llave. La participación por hash se lleva a cabo calculando un valor con la shard key previamente seleccionada, la cuál se utilizará para determinar el rango correspondiente a cada chunk.

El mismo administrador de la base de datos es el encargado de elegir la shard key al habilitar las particiones de una colección o base de datos, por lo cual, es necesario que se le ponga bastante atención a la decisión que se tomará, puesto que esto puede generar una distribución desigual y problemas de rendimiento si no se piensa con detenimiento.

3.- Aui entra en juego el llamado “Mongos” siendo este el llamado query router, recibiendo las operaciones de las aplicaciones y decidir que shard debe enviarse y a donde. Para ello, hace uso de los Config Servers, los cuales se encargan de almacenar los metadatos del cluster. Son estos los que almacenan por ekemplo, cuantos chunks tenemos, que rango abarca cada uno, y en que shard se almacena cada uno Mongos accede a estos datos y mantiene un cache para hacer sus envíos.

De forma muy simplificada, el flujo de una sconsulta se llevaría a cabo, primero, recibiendo la solicitud de la aplicación junto con el dato que guiará la búsqueda, mongos consulta sus metadatos y accede al mapa del kernel para buscar entre chunks y determinar, en cuál se encuentra la shard key que buscamos. Si no se proporciona la shard key al momento de realizar la solicitud para la búsqueda, mongos puede que no sepa donde se encuentre toda la información y complicar la búsqueda, haciendo una “Broadcast Operation”.

4.- Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?

Al momento de insertar un nuevo shard, MongoDB no requiere copiar todos los datos. Aquí entra en juego el llamado “Balancer, que se encarga de distribuir de forma más ordenada y equilibrada cada uno de los rangos o chunks que hayamos generado. No podemos decir que siempre se mueve una cuarta parte o un tercio de los datos, dependerá del modo en como los chunks y balanceador estén configurados, y como es que este último reordena los elementos en base a un desbalanceo previamente detectado.

5.- Qué hace con un shard caliente, si hace algo.

MongoDB incorpora mecanismos para tratar la aparición de un shard caliente. El uso de sharding por hash puede evitar que todos los datos se almacenen en un solo extremo del conjunto, o el balanceador puede distribuir de una mejor manera los chunks y rangos que decidamos generar, sin embargo, todo depende en gran parte de la elección de la shard key, la cuál, mongo db no puede alterar directamente o convertir una mala key en una buena, si no que depende completamente de la configuración que nosotros hayamos decidido darle.



Referencias:

1.- MongoDB. (s. f.). \*Particionado (sharding) — Manual de base de datos\*. MongoDB Docs. Recuperado el 9 de octubre de 2026, de https://www.mongodb.com/es/docs/manual/sharding/

2.- MongoDB. (s. f.). \*Sharded cluster components\*. MongoDB Docs. https://www.mongodb.com/docs/manual/core/sharded-cluster-components/

3.- MongoDB. (s. f.). \*Config servers\*. MongoDB Docs. https://www.mongodb.com/docs/manual/core/sharded-cluster-config-servers/

4.- MongoDB. (s. f.). \*Sharded cluster balancer\*. MongoDB Docs. https://www.mongodb.com/docs/manual/core/sharding-balancer-administration/











Creamos una carpeta para el compose.yaml.

```powershell

mkdir C:\\\\Users\\\\mauri\\\\mongo-sharding-t03 

cd C:\\\\Users\\\\mauri\\\\mongo-sharding-t03

```



Definimos el archive .yaml:

```yaml

services:

\&#x20; configsvr:

\&#x20;   image: mongo:8.0

\&#x20;   container\\\_name: t03-configsvr

\&#x20;   command: mongod --configsvr --replSet configRS --port 27019 --bind\\\_ip\\\_all

\&#x20;   volumes:

\&#x20;     - config\\\_data:/data/configdb

\&#x20;   networks:

\&#x20;     - mongo\\\_cluster



\&#x20; shard1:

\&#x20;   image: mongo:8.0

\&#x20;   container\\\_name: t03-shard1

\&#x20;   command: mongod --shardsvr --replSet shard1RS --port 27018 --bind\\\_ip\\\_all

\&#x20;   volumes:

\&#x20;     - shard1\\\_data:/data/db

\&#x20;   networks:

\&#x20;     - mongo\\\_cluster



\&#x20; shard2:

\&#x20;   image: mongo:8.0

\&#x20;   container\\\_name: t03-shard2

\&#x20;   command: mongod --shardsvr --replSet shard2RS --port 27018 --bind\\\_ip\\\_all

\&#x20;   volumes:

\&#x20;     - shard2\\\_data:/data/db

\&#x20;   networks:

\&#x20;     - mongo\\\_cluster



\&#x20; mongos:

\&#x20;   image: mongo:8.0

\&#x20;   container\\\_name: t03-mongos

\&#x20;   command: mongos --configdb configRS/configsvr:27019 --bind\\\_ip\\\_all --port 27017

\&#x20;   ports:

\&#x20;     - "27017:27017"

\&#x20;   depends\\\_on:

\&#x20;     - configsvr

\&#x20;     - shard1

\&#x20;     - shard2

\&#x20;   networks:

\&#x20;     - mongo\\\_cluster



networks:

\&#x20; mongo\\\_cluster:



volumes:

\&#x20; config\\\_data:

\&#x20; shard1\\\_data:

\&#x20; shard2\\\_data:



Levantamos el contenedor:

docker compose up -d

```



Inicializamos el replica set de configuración



```powershell

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-configsvr mongosh --port 27019 --eval "rs.initiate({\\\_id:'configRS',configsvr:true,members:\\\[{\\\_id:0,host:'configsvr:27019'}]})"

{

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791574309, i: 1 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791574309, i: 1 })

}



\\- Inicializamos los dos shards:

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-shard1 mongosh --port 27018 --eval "rs.initiate({\\\_id:'shard1RS',members:\\\[{\\\_id:0,host:'shard1:27018'}]})"

{

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791574399, i: 1 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791574399, i: 1 })

}

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-shard2 mongosh --port 27018 --eval "rs.initiate({\\\_id:'shard2RS',members:\\\[{\\\_id:0,host:'shard2:27018'}]})"

{

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791574405, i: 1 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791574405, i: 1 })

}

```



\-Agregamos ambos shards al cluster:



```powershell

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-mongos mongosh --port 27017 --eval "sh.addShard('shard1RS/shard1:27018')"

{

\&#x20; shardAdded: 'shard1RS',

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791574982, i: 20 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791574982, i: 20 })

}

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-mongos mongosh --port 27017 --eval "sh.addShard('shard2RS/shard2:27018')"

{

\&#x20; shardAdded: 'shard2RS',

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791574990, i: 24 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791574990, i: 18 })

}

```



\- Verificamos que MongoDB reconozca los dos shards:

```powershell

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-mongos mongosh --port 27017 --eval "db.adminCommand({listShards:1})"

{

\&#x20; shards: \\\[

\&#x20;   {

\&#x20;     \\\_id: 'shard1RS',

\&#x20;     host: 'shard1RS/shard1:27018',

\&#x20;     state: 1,

\&#x20;     topologyTime: Timestamp({ t: 1791574982, i: 10 }),

\&#x20;     replSetConfigVersion: Long('-1')

\&#x20;   },

\&#x20;   {

\&#x20;     \\\_id: 'shard2RS',

\&#x20;     host: 'shard2RS/shard2:27018',

\&#x20;     state: 1,

\&#x20;     topologyTime: Timestamp({ t: 1791574990, i: 9 }),

\&#x20;     replSetConfigVersion: Long('-1')

\&#x20;   }

\&#x20; ],

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791575086, i: 1 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791575086, i: 1 })

}

```powershell

\\-Habilitamos el sharding para nuestra base de datos.

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-mongos mongosh --port 27017 --eval "sh.enableSharding('t03\\\_db')"

{

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791575258, i: 8 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791575258, i: 5 })

}

```

Creamos una colección fragmentada. Usaremos estudiantes con matricula como clave de shard. Para este ejemplo, utilizaremos sharding por hash, que calcula un valor hash de la clave para ayudar a distribuir los documentos.



```powershell

docker exec t03-mongos mongosh --port 27017 --eval "db.getSiblingDB('t03\\\_db').estudiantes.createIndex({matricula:'hashed'})"

```



Fragmentamos la colección:



```powershell

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-mongos mongosh --port 27017 --eval "sh.shardCollection('t03\\\_db.estudiantes',{matricula:'hashed'})"

{

\&#x20; collectionsharded: 't03\\\_db.estudiantes',

\&#x20; ok: 1,

\&#x20; '$clusterTime': {

\&#x20;   clusterTime: Timestamp({ t: 1791575571, i: 50 }),

\&#x20;   signature: {

\&#x20;     hash: Binary.createFromBase64('AAAAAAAAAAAAAAAAAAAAAAAAAAA=', 0),

\&#x20;     keyId: Long('0')

\&#x20;   }

\&#x20; },

\&#x20; operationTime: Timestamp({ t: 1791575571, i: 49 })

}

```



Insertamos documentos de prueba, en este caso, 20 estudiantes con matrículas del 1000 al 1019.

```powershell



PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-mongos mongosh --port 27017 --eval "db.getSiblingDB('t03\\\_db').estudiantes.insertMany(Array.from({length:20},(\\\_,i)=>({matricula:1000+i,nombre:'Estudiante\\\_'+(i+1),semestre:(i%9)+1})))"

{

\&#x20; acknowledged: true,

\&#x20; insertedIds: {

\&#x20;   '0': ObjectId('6ac946a5d08055bdf11ad9fc'),

\&#x20;   '1': ObjectId('6ac946a5d08055bdf11ad9fd'),

\&#x20;   '2': ObjectId('6ac946a5d08055bdf11ad9fe'),

\&#x20;   '3': ObjectId('6ac946a5d08055bdf11ad9ff'),

\&#x20;   '4': ObjectId('6ac946a5d08055bdf11ada00'),

\&#x20;   '5': ObjectId('6ac946a5d08055bdf11ada01'),

\&#x20;   '6': ObjectId('6ac946a5d08055bdf11ada02'),

\&#x20;   '7': ObjectId('6ac946a5d08055bdf11ada03'),

\&#x20;   '8': ObjectId('6ac946a5d08055bdf11ada04'),

\&#x20;   '9': ObjectId('6ac946a5d08055bdf11ada05'),

\&#x20;   '10': ObjectId('6ac946a5d08055bdf11ada06'),

\&#x20;   '11': ObjectId('6ac946a5d08055bdf11ada07'),

\&#x20;   '12': ObjectId('6ac946a5d08055bdf11ada08'),

\&#x20;   '13': ObjectId('6ac946a5d08055bdf11ada09'),

\&#x20;   '14': ObjectId('6ac946a5d08055bdf11ada0a'),

\&#x20;   '15': ObjectId('6ac946a5d08055bdf11ada0b'),

\&#x20;   '16': ObjectId('6ac946a5d08055bdf11ada0c'),

\&#x20;   '17': ObjectId('6ac946a5d08055bdf11ada0d'),

\&#x20;   '18': ObjectId('6ac946a5d08055bdf11ada0e'),

\&#x20;   '19': ObjectId('6ac946a5d08055bdf11ada0f')

\&#x20; }

} 

```



Consultamos los chunks de la colección:



```powershell

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-shard1 mongosh --port 27018 --eval "db.getSiblingDB('t03\\\_db').estudiantes.find({}, {\\\_id:0,matricula:1,nombre:1}).toArray()"

\\\[

\&#x20; { matricula: 1004, nombre: 'Estudiante\\\_5' },

\&#x20; { matricula: 1007, nombre: 'Estudiante\\\_8' },

\&#x20; { matricula: 1008, nombre: 'Estudiante\\\_9' },

\&#x20; { matricula: 1010, nombre: 'Estudiante\\\_11' },

\&#x20; { matricula: 1011, nombre: 'Estudiante\\\_12' },

\&#x20; { matricula: 1019, nombre: 'Estudiante\\\_20' }

]

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-shard2 mongosh --port 27018 --eval "db.getSiblingDB('t03\\\_db').estudiantes.find({}, {\\\_id:0,matricula:1,nombre:1}).toArray()"

\\\[

\&#x20; { matricula: 1000, nombre: 'Estudiante\\\_1' },

\&#x20; { matricula: 1001, nombre: 'Estudiante\\\_2' },

\&#x20; { matricula: 1002, nombre: 'Estudiante\\\_3' },

\&#x20; { matricula: 1003, nombre: 'Estudiante\\\_4' },

\&#x20; { matricula: 1005, nombre: 'Estudiante\\\_6' },

\&#x20; { matricula: 1006, nombre: 'Estudiante\\\_7' },

\&#x20; { matricula: 1009, nombre: 'Estudiante\\\_10' },

\&#x20; { matricula: 1012, nombre: 'Estudiante\\\_13' },

\&#x20; { matricula: 1013, nombre: 'Estudiante\\\_14' },

\&#x20; { matricula: 1014, nombre: 'Estudiante\\\_15' },

\&#x20; { matricula: 1015, nombre: 'Estudiante\\\_16' },

\&#x20; { matricula: 1016, nombre: 'Estudiante\\\_17' },

\&#x20; { matricula: 1017, nombre: 'Estudiante\\\_18' },

\&#x20; { matricula: 1018, nombre: 'Estudiante\\\_19' }

]

```



Finalmente, consultamos los documentos almacenados directamente en cada shard y verificamos el estado del clúster mediante sh.status().



```powershell

PS C:\\\\Users\\\\mauri\\\\mongo-sharding-t03> docker exec t03-mongos mongosh --port 27017 --eval "sh.status()"

shardingVersion

{ \\\_id: 1, clusterId: ObjectId('6ac941258607d0fcdf88ae7d') }

\\---

shards

\\\[

\&#x20; {

\&#x20;   \\\_id: 'shard1RS',

\&#x20;   host: 'shard1RS/shard1:27018',

\&#x20;   state: 1,

\&#x20;   topologyTime: Timestamp({ t: 1791574982, i: 10 }),

\&#x20;   replSetConfigVersion: Long('-1')

\&#x20; },

\&#x20; {

\&#x20;   \\\_id: 'shard2RS',

\&#x20;   host: 'shard2RS/shard2:27018',

\&#x20;   state: 1,

\&#x20;   topologyTime: Timestamp({ t: 1791574990, i: 9 }),

\&#x20;   replSetConfigVersion: Long('-1')

\&#x20; }

]

\\---

active mongoses

\\\[ { '8.0.32': 1 } ]

\\---

autosplit

{ 'Currently enabled': 'yes' }

\\---

balancer

{

\&#x20; 'Currently running': 'no',

\&#x20; 'Currently enabled': 'yes',

\&#x20; 'Failed balancer rounds in last 5 attempts': 0,

\&#x20; 'Migration Results for the last 24 hours': 'No recent migrations'

}

\\---

shardedDataDistribution

\\\[

\&#x20; {

\&#x20;   ns: 't03\\\_db.estudiantes',

\&#x20;   shards: \\\[

\&#x20;     {

\&#x20;       shardName: 'shard1RS',

\&#x20;       numOrphanedDocs: 0,

\&#x20;       numOwnedDocuments: 6,

\&#x20;       ownedSizeBytes: 456,

\&#x20;       orphanedSizeBytes: 0

\&#x20;     },

\&#x20;     {

\&#x20;       shardName: 'shard2RS',

\&#x20;       numOrphanedDocs: 0,

\&#x20;       numOwnedDocuments: 14,

\&#x20;       ownedSizeBytes: 1064,

\&#x20;       orphanedSizeBytes: 0

\&#x20;     }

\&#x20;   ]

\&#x20; },

\&#x20; {

\&#x20;   ns: 'config.system.sessions',

\&#x20;   shards: \\\[

\&#x20;     {

\&#x20;       shardName: 'shard1RS',

\&#x20;       numOrphanedDocs: 0,

\&#x20;       numOwnedDocuments: 27,

\&#x20;       ownedSizeBytes: 2673,

\&#x20;       orphanedSizeBytes: 0

\&#x20;     }

\&#x20;   ]

\&#x20; }

]

\\---

databases

\\\[

\&#x20; {

\&#x20;   database: { \\\_id: 'config', primary: 'config', partitioned: true },

\&#x20;   collections: {

\&#x20;     'config.system.sessions': {

\&#x20;       shardKey: { \\\_id: 1 },

\&#x20;       unique: false,

\&#x20;       balancing: true,

\&#x20;       allowMigrations: true,

\&#x20;       chunkMetadata: \\\[ { shard: 'shard1RS', nChunks: 1 } ],

\&#x20;       chunks: \\\[

\&#x20;         { min: { \\\_id: MinKey() }, max: { \\\_id: MaxKey() }, 'on shard': 'shard1RS', 'last modified': Timestamp({ t: 1, i: 0 }) }

\&#x20;       ],

\&#x20;       tags: \\\[]

\&#x20;     }

\&#x20;   }

\&#x20; },

\&#x20; {

\&#x20;   database: {

\&#x20;     \\\_id: 't03\\\_db',

\&#x20;     primary: 'shard2RS',

\&#x20;     version: {

\&#x20;       uuid: UUID('ad4d89ed-3551-45a7-ae76-5ef758560137'),

\&#x20;       timestamp: Timestamp({ t: 1791575258, i: 2 }),

\&#x20;       lastMod: 1

\&#x20;     }

\&#x20;   },

\&#x20;   collections: {

\&#x20;     't03\\\_db.estudiantes': {

\&#x20;       shardKey: { matricula: 'hashed' },

\&#x20;       unique: false,

\&#x20;       balancing: true,

\&#x20;       allowMigrations: true,

\&#x20;       chunkMetadata: \\\[

\&#x20;         { shard: 'shard1RS', nChunks: 1 },

\&#x20;         { shard: 'shard2RS', nChunks: 1 }

\&#x20;       ],

\&#x20;       chunks: \\\[

\&#x20;         { min: { matricula: MinKey() }, max: { matricula: Long('0') }, 'on shard': 'shard2RS', 'last modified': Timestamp({ t: 1, i: 0 }) },

\&#x20;         { min: { matricula: Long('0') }, max: { matricula: MaxKey() }, 'on shard': 'shard1RS', 'last modified': Timestamp({ t: 1, i: 1 }) }

\&#x20;       ],

\&#x20;       tags: \\\[]

\&#x20;     }

\&#x20;   }

\&#x20; }

]

```

En la salida comprobamos que MongoDB reconoce los dos shards (shard1RS y shard2RS) y que la colección t03\_db.estudiantes está fragmentada mediante la clave { matricula: "hashed" }. También se reportan 6 documentos en shard1RS y 14 en shard2RS, para un total de 20 documentos distribuidos entre ambos shards.



