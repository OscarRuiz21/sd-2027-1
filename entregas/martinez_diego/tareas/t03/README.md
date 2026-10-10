# T03 - Una Implementación Real de Sharding: MongoDB Sharded Cluster

## 1. ¿Qué es MongoDB y para qué se usa?
MongoDB es una base de datos NoSQL orientada a documentos distribuida. En lugar de almacenar datos en tablas y filas como en las bases de datos relacionales, guarda la información en formato BSON (una representación binaria de JSON).

Se utiliza ampliamente en aplicaciones web, sistemas en tiempo real y arquitecturas de microservicios por su alta flexibilidad de esquema, alta disponibilidad y su capacidad nativa para escalado horizontal (Sharding).

---

## 2. ¿Cómo reparte los datos? (Estrategias de Sharding)
MongoDB divide las colecciones en bloques de datos llamados Chunks. La forma en que reparte estos datos entre los diferentes nodos (shards) depende de la Shard Key (Clave de Fragmentación) seleccionada:

* **Sharding por Rango (Ranged Sharding):**
  * Los datos se dividen según rangos contiguos de valores de la shard key.
  * *Ventaja:* Facilita las consultas por rango (ej. `precio > 100 y precio < 200`).
  * *Riesgo:* Puede provocar escrituras no uniformes (hotspots) si la clave es monolítica (por ejemplo, fechas o autonuméricos en crecimiento constante).
* **Sharding por Hash (Hashed Sharding):**
  * Se calcula un *MD5 hash* del campo elegido como shard key para determinar en qué chunk/shard se almacena el documento.
  * *Ventaja:* Garantiza una distribución aleatoria y uniforme de las escrituras a lo largo de todo el clúster.
  * *Desventaja:* Las consultas por rango se vuelven ineficientes ya que requieren consultar a todos los shards (broadcast queries).
* **Otras Estrategias (Zone / Tag-Aware Sharding):**
  * Permite asociar rangos o hashes de la shard key a shards específicos (por ejemplo, por ubicación geográfica para cumplir normativas de soberanía de datos).

---

## 3. ¿Quién encuentra el shard correcto?
La arquitectura de MongoDB Sharding utiliza tres componentes principales:

1. **`mongos` (Query Router):** Actúa como proxy o router entre las aplicaciones cliente y el clúster. Recibe las peticiones, consulta la metadata para saber a qué shard dirigir la solicitud, y consolida los resultados.
2. **Config Servers (CSRS - Config Server Replica Set):** Guardan la metadata del clúster (mapeo de qué chunks residen en qué shard y los rangos de las shard keys).
3. **Shards:** Nodos de almacenamiento reales (cada shard suele ser un Replica Set para brindar tolerancia a fallos).

         [ Cliente ]
              |
              v
          [ mongos ] <---- Consultan metadata
              |         |  [ Config Servers ]
    +---------+---------+
    |         |         |
    v         v         v
 Shard 1   Shard 2   Shard 3

---

## 4. ¿Qué pasa cuando agregamos un shard?
Cuando se añade un nuevo shard al clúster, el proceso **Balancer** (que corre internamente en el clúster) detecta el desbalanceo en la cantidad de *chunks* entre los shards disponibles.

1. **Migración de Chunks:** El Balancer selecciona chunks de los shards con mayor carga y los transfiere al nuevo shard de manera asíncrona.
2. **Sin Interrupción:** Durante la migración, la base de datos sigue respondiendo lecturas y escrituras sin necesidad de reiniciar o interrumpir el servicio.
3. **Actualización de Metadata:** Una vez transferido el chunk, los Config Servers actualizan la tabla de ruteo y `mongos` redirige las futuras peticiones de esos rangos al nuevo shard.

---

## 5. ¿Qué pasa con un shard caliente (*Hotspot / Hot Shard*)?
Un **Hot Shard** ocurre cuando un único nodo recibe un volumen desproporcionado de peticiones o almacenamiento en comparación con los demás.

* **Causa principal:** Una mala elección de la *Shard Key* (por ejemplo, usar campos de baja cardinalidad como `genero` o campos estrictamente crecientes como `timestamp` en Ranged Sharding).
* **Solución y Mitigación:**
  * El Balancer tratará de redistribuir chunks, pero si todos los registros con la misma shard key pertenecen al mismo rango indivisible, el Balancer **no podrá** moverlos a otros shards.
  * Se requiere cambiar a una Shard Key compuesta o por Hash (*Hashed Shard Key*) para distribuir uniformemente los registros.

---

## 6. Ejemplo Práctico con Docker (Demostración)

A continuación se muestra una configuración representativa mediante `docker-compose` para desplegar la arquitectura completa:

### Archivo `docker/docker-compose.yml`
```yaml
version: '3.8'

services:
  # Config Server
  configsvr:
    image: mongo:6.0
    container_name: mongo-config
    command: mongod --configsvr --replSet configReplSet --port 27019 --bind_ip_all
    ports:
      - "27019:27019"

  # Shard 1
  shard1:
    image: mongo:6.0
    container_name: mongo-shard1
    command: mongod --shardsvr --replSet shard1ReplSet --port 27018 --bind_ip_all
    ports:
      - "27018:27018"

  # Router mongos
  mongos:
    image: mongo:6.0
    container_name: mongo-mongos
    command: mongos --configdb configReplSet/configsvr:27019 --bind_ip_all --port 27017
    ports:
      - "27017:27017"
    depends_on:
      - configsvr
      - shard1

7. Conclusiones
MongoDB implementa el sharding de forma transparente hacia la aplicación gracias al router mongos.

La elección de una buena Shard Key es crítica para garantizar el rendimiento, prevenir hotspots y permitir un balanceo efectivo.

Sharding no reemplaza la replicación (Alta Disponibilidad), sino que se complementan: cada Shard se implementa como un Replica Set.

## 8. Fuentes

* MongoDB. (2026). *Config server replica sets*. MongoDB Manual. Recuperado el 6 de octubre de 2026, de https://www.mongodb.com/docs/manual/core/sharded-cluster-components/
* MongoDB. (2026). *Hashed sharding*. MongoDB Manual. Recuperado el 6 de octubre de 2026, de https://www.mongodb.com/docs/manual/core/hashed-sharding/
* MongoDB. (2026). *Sharding*. MongoDB Manual. Recuperado el 6 de octubre de 2026, de https://www.mongodb.com/docs/manual/sharding/
