# T03 · Una implementación real de Sharding — MongoDB

**Nombre completo:** Muñoz San Agustin Victoria Monserrat

## 1. ¿Qué es MongoDB y para qué se usa el sharding?

MongoDB es una base de datos que almacena información en documentos. El *sharding* se utiliza cuando la cantidad de datos o el volumen de consultas supera la capacidad práctica de un solo servidor. Consiste en distribuir los datos entre varios servidores, llamados *shards*, para repartir el almacenamiento y la carga de trabajo.

MongoDB utiliza una arquitectura compuesta por los shards, los servidores de configuración (*config servers*) y el router `mongos`. Los servidores de configuración almacenan los metadatos del clúster, mientras que `mongos` dirige las consultas hacia los shards correspondientes.

## 2. ¿Cómo reparte los datos: por rango, por hash o de otra forma? ¿Quién elige la llave?

MongoDB permite distribuir los datos principalmente mediante *sharding* por rango o por hash. Para determinar dónde se almacena cada documento, se selecciona una **shard key**, formada por uno o varios campos del documento.

La elección de la shard key corresponde al diseño de la base de datos y debe considerar la distribución de los datos y las consultas más frecuentes. Una selección inadecuada puede provocar una distribución desigual o concentrar la carga en determinados shards.

En esta práctica se utilizó `_id` como shard key mediante una estrategia basada en rangos.

## 3. ¿Cómo encuentra el shard correcto cuando llega una consulta?

MongoDB utiliza el componente `mongos` como intermediario entre la aplicación y los shards. El router consulta los metadatos del clúster, almacenados en los config servers, para determinar qué shard contiene el rango correspondiente a la shard key.

Cuando una consulta contiene la shard key, `mongos` puede dirigirla al shard o a los shards relevantes. Si la consulta no permite determinar un destino específico, puede ser necesario consultar varios shards y combinar los resultados.

## 4. ¿Qué pasa cuando agregas un shard? ¿Se mueven los datos?

Al agregar un shard, MongoDB puede redistribuir los datos para aprovechar la capacidad del nuevo servidor. El proceso de balanceo puede migrar chunks entre los shards para mejorar la distribución de los datos.

No es necesario mover todos los documentos: las migraciones afectan a los rangos que deben cambiar de ubicación para conseguir una distribución más equilibrada.

## 5. ¿Qué hace MongoDB con un shard caliente?

Un shard caliente es un servidor que recibe una cantidad desproporcionada de consultas o trabajo respecto a los demás. El balanceador puede ayudar a redistribuir chunks, pero esto no resuelve necesariamente todos los problemas de carga.

Si el origen del desequilibrio es una shard key poco adecuada, la estrategia de balanceo por sí sola puede resultar insuficiente. Por ello, la elección de la clave debe considerar la distribución de los valores y los patrones de acceso a los datos.

---

## 6. Implementación práctica: sharding con Docker

Para comprobar el funcionamiento del sharding, se desplegó un clúster de MongoDB utilizando Docker Compose.

La arquitectura se compuso de:

- Un config server.
- Dos shards: `shard1RS` y `shard2RS`.
- Un router `mongos` para acceder al clúster.

La configuración se definió en `docker-compose.yml`, utilizando la imagen `mongo:8.0` y volúmenes independientes para conservar los datos de cada componente.

### 6.1 Levantar el clúster

Se ejecutó el siguiente comando:

```bash
docker compose up -d
```

Para verificar los contenedores:

```bash
docker compose ps
```

Después, se abrió una sesión de MongoDB Shell a través del router:

```bash
docker compose exec mongos mongosh --port 27017
```

### 6.2 Agregar los shards

Se incorporaron los dos conjuntos de réplicas al clúster:

```javascript
sh.addShard("shard1RS/shard1:27018")
sh.addShard("shard2RS/shard2:27020")
```

### 6.3 Habilitar el sharding y configurar la colección

Se habilitó el sharding para la base de datos `tienda`:

```javascript
sh.enableSharding("tienda")
```

A continuación, se configuró la colección `tienda.productos` para distribuir sus documentos por rangos de `_id`:

```javascript
sh.shardCollection("tienda.productos", { _id: 1 })
```

### 6.4 Dividir los rangos

Se utilizó el valor `_id: 50` como punto de separación entre los rangos:

```javascript
sh.splitAt(
  "tienda.productos",
  { _id: 50 }
)
```

Después, se movió el rango correspondiente a partir de `_id: 50` al segundo shard:

```javascript
sh.moveChunk(
  "tienda.productos",
  { _id: 50 },
  "shard2RS"
)
```

De acuerdo con esta configuración, se definieron dos rangos:

- Desde `MinKey` hasta `50`, en `shard1RS`.
- Desde `50` hasta `MaxKey`, en `shard2RS`.

### 6.5 Insertar documentos de prueba

Se insertaron 100 documentos en la colección, con identificadores del 1 al 100:

```javascript
for (let i = 1; i <= 100; i++) {
  db.productos.insertOne({
    _id: i,
    producto: "Producto " + i,
    precio: i * 10
  })
}
```

### 6.6 Comprobar la distribución

Se ejecutó:

```javascript
db.productos.getShardDistribution()
```

La salida obtenida mostró los siguientes resultados:

| Shard | Documentos | Porcentaje de documentos |
|---|---:|---:|
| `shard1RS` | 49 | 49 % |
| `shard2RS` | 51 | 51 % |
| **Total** | **100** | **100 %** |

La distribución observada fue cercana al equilibrio, con una diferencia de dos documentos entre ambos shards.

### 6.7 Comprobar el estado del clúster

Se ejecutó:

```javascript
sh.status()
```

La salida confirmó la presencia de los dos shards, la colección `tienda.productos`, la shard key `{ _id: 1 }` y dos chunks asociados a los rangos definidos.

El apartado `balancer` también mostró que el balanceador estaba habilitado y que se había registrado una migración exitosa. Esto permitió comprobar el estado de la configuración y la distribución de los datos en el clúster.

## 7. Evidencias de funcionamiento

### 7.1 Estado del clúster

La siguiente captura corresponde a la salida de `sh.status()`, donde se observan los dos shards, la configuración de la colección y el estado del balanceador.

![Evidencia del estado del clúster MongoDB](sh.status().png)

### 7.2 Distribución de documentos

La siguiente captura corresponde a `db.productos.getShardDistribution()`, que muestra los documentos distribuidos entre ambos shards y el total de 100 documentos.

![Evidencia de la distribución de documentos](db.productos.getShardDistribution().png)

## 8. Resultado y conclusión

La práctica permitió comprobar cómo MongoDB distribuye una colección entre varios shards mediante una shard key y una estrategia basada en rangos. Se configuró un clúster con dos shards, se insertaron 100 documentos y se verificó una distribución de 49 documentos en `shard1RS` y 51 en `shard2RS`.

Además, mediante `sh.status()` se revisó la configuración del clúster, los chunks y el estado del balanceador. Los resultados demostraron el funcionamiento de la distribución de datos en el entorno de prueba y permitieron relacionar los conceptos de sharding, shard keys y balanceo con una implementación práctica.

## 9. Fuentes

1. MongoDB, Inc. (s. f.). *Sharding*. MongoDB Database Manual. https://www.mongodb.com/docs/manual/sharding/

2. MongoDB, Inc. (s. f.). *Shard Keys*. MongoDB Database Manual. https://www.mongodb.com/docs/manual/core/sharding-shard-key/

3. MongoDB, Inc. (s. f.). *Sharded Cluster Balancer*. MongoDB Database Manual. https://www.mongodb.com/docs/manual/core/sharding-balancer-administration/
