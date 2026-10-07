\## Parte opcional: Implementación de sharding con Docker



Para comprobar de manera práctica el funcionamiento del sharding, se

levantó un cluster de MongoDB utilizando Docker.



El cluster está compuesto por un Config Server, dos shards

(`shard1RS` y `shard2RS`) y un router `mongos`.



Primero se habilitó el sharding para la base de datos `tienda` y se creó

la colección `tienda.productos`. Se utilizó `\_id` como shard key mediante

sharding por rango.



Después se dividió la colección utilizando `\_id = 50` como punto de

separación. El primer rango quedó en `shard1RS` y el segundo rango fue

movido a `shard2RS`.



Los rangos quedaron de la siguiente manera:



\- `MinKey → 50` → `shard1RS`

\- `50 → MaxKey` → `shard2RS`



Posteriormente se insertaron 100 productos, con valores de `\_id` del 1

al 100.



Al ejecutar:



&#x20;   db.productos.getShardDistribution()



se obtuvo:



\- `shard1RS`: 49 documentos

\- `shard2RS`: 51 documentos

\- Total: 100 documentos



También se ejecutó:



&#x20;   sh.status()



para comprobar la configuración del cluster. La salida confirmó que

`tienda.productos` tiene dos chunks, uno en cada shard.



Finalmente, el estado del balancer mostró que estaba habilitado y que

se había realizado una migración correctamente.



\### Resultado



La práctica permitió comprobar que MongoDB puede distribuir una

colección entre diferentes servidores utilizando una shard key. En este

caso, `\_id` fue utilizado para dividir los documentos por rango y

MongoDB se encargó de dirigir y almacenar los datos en el shard

correspondiente.



\### Comandos utilizados



Levantar el cluster:



&#x20;   docker compose up -d



Comprobar los contenedores:



&#x20;   docker compose ps



Entrar a mongos:



&#x20;   docker compose exec mongos mongosh --port 27017



Agregar los shards:



&#x20;   sh.addShard("shard1RS/shard1:27018")

&#x20;   sh.addShard("shard2RS/shard2:27020")



Habilitar sharding:



&#x20;   sh.enableSharding("tienda")



Crear colección shardeada:



&#x20;   sh.shardCollection("tienda.productos", { \_id: 1 })



Dividir el rango:



&#x20;   sh.splitAt(

&#x20;     "tienda.productos",

&#x20;     { \_id: 50 }

&#x20;   )



Mover el segundo rango:



&#x20;   sh.moveChunk(

&#x20;     "tienda.productos",

&#x20;     { \_id: 50 },

&#x20;     "shard2RS"

&#x20;   )



Insertar 100 documentos:



&#x20;   for (let i = 1; i <= 100; i++) {

&#x20;     db.productos.insertOne({

&#x20;       \_id: i,

&#x20;       producto: "Producto " + i,

&#x20;       precio: i \* 10

&#x20;     })

&#x20;   }



Comprobar distribución:



&#x20;   db.productos.getShardDistribution()



Comprobar el estado del cluster:



&#x20;   sh.status()

