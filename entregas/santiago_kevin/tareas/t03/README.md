# T03 · Una implementación real de sharding: MongoDB

Nombre: Kevin Santiago González

## Pregunta 1: ¿Qué es MongoDB y para qué se usa?

MongoDB es una base de datos orientada a documentos. En lugar de trabajar con tablas como las bases de datos SQL, MongoDB trabaja con documentos descritos en BSON. Se usa cuando se tienen decenas o cientos de fuentes de datos, ya que permite crear una vista unificada. Además, gestiona un gran número de transacciones en bases de datos gigantescas. Asimismo, se usa para describir estructuras unidas que tienden a presentar variaciones en los datos entre generaciones de documentos.

MongoDB utiliza la fragmentación para dar soporte a las implementaciones con conjuntos de datos muy grandes y operaciones de alto rendimiento. Esto implica dividir el conjunto de datos y la carga del sistema entre varios servidores, agregando más servidores para aumentar la capacidad según sea necesario e incrementando la capacidad de almacenamiento del clúster.

## Pregunta 2: ¿Cómo reparte los datos: por rango, por hash o de otra forma? ¿Quién elige la llave?

MongoDB utiliza la clave de fragmentación para distribuir los documentos de la colección entre fragmentos. Esta clave consiste en uno o varios campos dentro de los documentos. El desarrollador o administrador elige la clave de fragmentación al fragmentar una colección. MongoDB ofrece dos tipos de estrategias para distribuir datos:

- **Por rango:** agrupa valores cercanos de la clave. Sin embargo, todo depende de la clave de fragmentación, ya que una mal diseñada implica una distribución desigual de los datos.
- **Por hash:** calcula un hash del valor y distribuye los documentos según el rango de esos hashes.

## Pregunta 3: ¿Cómo encuentra el shard correcto cuando llega una consulta? ¿Quién guarda el mapa de qué dato vive dónde?

Se envía una consulta a `mongos`, el enrutador. Los servidores de configuración guardan los metadatos que indican dónde están los rangos.

Si la consulta permite identificar los rangos necesarios mediante la clave, `mongos` la dirige a los shards correspondientes; de lo contrario, puede consultar todos y reunir sus respuestas.

## Pregunta 4: ¿Qué pasa cuando agregas un shard? ¿Se mueven los datos? ¿Cuántos?

El nuevo shard empieza vacío. El balanceador migra datos de otros shards para balancear los datos de cada colección. El balanceador inicia las migraciones cuando la diferencia en la cantidad de datos de una colección entre shards alcanza el umbral de migración.

No existe una cantidad fija de datos que se mueven; depende del desequilibrio, los umbrales y las restricciones configuradas.

## Pregunta 5: ¿Qué hace con un shard caliente, si hace algo?

Un shard caliente recibe demasiadas operaciones. El balanceador puede redistribuir datos, pero equilibra el volumen de datos, no directamente la cantidad de consultas o el uso de CPU. Por eso, no garantiza eliminar un punto caliente. Si el problema viene de una mala clave de partición, hay que revisar su elección. Agregar shards por sí solo puede no resolverlo.

## Parte opcional: implementación con Docker

Utilicé MongoDB 7.0 y Docker Compose para crear un clúster con cuatro contenedores:

- `configsvr`: almacena los metadatos de distribución.
- `shard1`: almacena una parte de los documentos.
- `shard2`: almacena la otra parte.
- `mongos`: recibe las consultas y las dirige a los shards.

La configuración está en el archivo `docker-compose.yml` de esta carpeta.

### 1. Arranque e inicialización

Los siguientes comandos se ejecutaron desde la carpeta `t03`, para un clúster nuevo:

```bash
docker compose up -d configsvr shard1 shard2

docker compose exec configsvr mongosh --port 27019 --quiet --eval 'rs.initiate({_id:"configRS",configsvr:true,members:[{_id:0,host:"configsvr:27019"}]})'

docker compose exec shard1 mongosh --port 27018 --quiet --eval 'rs.initiate({_id:"shard1RS",members:[{_id:0,host:"shard1:27018"}]})'

docker compose exec shard2 mongosh --port 27018 --quiet --eval 'rs.initiate({_id:"shard2RS",members:[{_id:0,host:"shard2:27018"}]})'

docker compose up -d mongos

docker compose exec mongos mongosh --quiet --eval 'sh.addShard("shard1RS/shard1:27018")'

docker compose exec mongos mongosh --quiet --eval 'sh.addShard("shard2RS/shard2:27018")'
```

### 2. Creación de la colección y distribución por rango

Creé la base de datos `escuela` y la colección `alumnos`. Elegí `alumnoId` como clave de fragmentación.

```bash
docker compose exec mongos mongosh --quiet --eval 'sh.enableSharding("escuela", "shard1RS")'

docker compose exec mongos mongosh --quiet --eval 'sh.shardCollection("escuela.alumnos", {alumnoId:1})'

docker compose exec mongos mongosh --quiet --eval 'sh.splitAt("escuela.alumnos", {alumnoId:4})'

docker compose exec mongos mongosh --quiet --eval 'sh.moveChunk("escuela.alumnos", {alumnoId:4}, "shard2RS")'
```

Dividí manualmente los rangos y moví el segundo al shard 2 antes de insertar los documentos:

| Rango | Shard |
|---|---|
| `alumnoId < 4` | `shard1RS` |
| `alumnoId >= 4` | `shard2RS` |

### 3. Inserción de documentos

Envié los seis documentos a través de `mongos`:

```bash
docker compose exec mongos mongosh --quiet --eval 'db.getSiblingDB("escuela").alumnos.insertMany([
  {_id:1, alumnoId:1, nombre:"Ana"},
  {_id:2, alumnoId:2, nombre:"Luis"},
  {_id:3, alumnoId:3, nombre:"Kevin"},
  {_id:4, alumnoId:4, nombre:"Maria"},
  {_id:5, alumnoId:5, nombre:"Pedro"},
  {_id:6, alumnoId:6, nombre:"Sofia"}
])'
```

Salida obtenida:

```text
{
  acknowledged: true,
  insertedIds: { '0': 1, '1': 2, '2': 3, '3': 4, '4': 5, '5': 6 }
}
```

### 4. Comprobación de los documentos en cada shard

Consulté directamente cada shard para inspeccionar la distribución. 

**Consulta al primer shard:**

```bash
docker compose exec shard1 mongosh --port 27018 --quiet --eval 'db.getSiblingDB("escuela").alumnos.find().sort({alumnoId:1}).toArray()'
```

Salida obtenida:

```text
[
  { _id: 1, alumnoId: 1, nombre: 'Ana' },
  { _id: 2, alumnoId: 2, nombre: 'Luis' },
  { _id: 3, alumnoId: 3, nombre: 'Kevin' }
]
```

**Consulta al segundo shard:**

```bash
docker compose exec shard2 mongosh --port 27018 --quiet --eval 'db.getSiblingDB("escuela").alumnos.find().sort({alumnoId:1}).toArray()'
```

Salida obtenida:

```text
[
  { _id: 4, alumnoId: 4, nombre: 'Maria' },
  { _id: 5, alumnoId: 5, nombre: 'Pedro' },
  { _id: 6, alumnoId: 6, nombre: 'Sofia' }
]
```

### Resultado

Comprobé que una misma colección puede estar repartida entre dos shards. Los documentos con `alumnoId` menor que 4 quedaron en el primer shard, y los documentos con valores mayores o iguales a 4 quedaron en el segundo. 

### Fuentes

1. MongoDB. Particionado.
   https://www.mongodb.com/es/docs/manual/sharding/

2. MongoDB. Balanceador del clúster particionado.
   https://www.mongodb.com/es/docs/manual/core/sharding-balancer-administration/

3. MongoDB. Implementación de un clúster fragmentado:
   https://www.mongodb.com/docs/manual/tutorial/deploy-shard-cluster/

4. MongoDB. División de rangos con `sh.splitAt()`:
   https://www.mongodb.com/docs/v7.0/reference/method/sh.splitAt/

5. MongoDB. Movimiento de rangos con `sh.moveChunk()`:
   https://www.mongodb.com/docs/v7.0/reference/method/sh.moveChunk/