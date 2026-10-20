# T03 · Sharding en MongoDB

Derek Santana Cardoso · Sistemas Distribuidos 2027-1 · 19 de octubre de 2026.
Preparado con asistencia de IA y documentación oficial. Incluye el trabajo
obligatorio; no se afirma haber ejecutado el reto opcional en Docker.

## 1. Qué es y para qué se usa

MongoDB es una base de datos de documentos BSON. Su sharding reparte los datos
de una colección entre varios shards para distribuir almacenamiento y carga.
En cambio, la replicación mantiene copias de los mismos datos. En producción,
cada shard puede ser un replica set: distribución y disponibilidad cumplen
funciones distintas.

En nuestra tienda podría estudiarse para un catálogo grande. Un catálogo pequeño
no justifica el costo de operar un clúster. Sharding tampoco garantiza que cada
consulta sea más rápida: depende de la llave y del acceso.

Fuente: [MongoDB, Sharding](https://www.mongodb.com/docs/manual/sharding/).

## 2. Cómo reparte los datos y quién elige la llave

El responsable del diseño elige uno o varios campos como shard key. MongoDB
divide su espacio en rangos llamados chunks y asigna esos rangos a shards.

Con **range sharding**, se usan directamente los valores de la llave. Ayuda a
consultas de rango, pero un valor creciente puede concentrar las nuevas escrituras
en un extremo. Con **hashed sharding**, se calcula un hash y se reparten rangos
de ese hash. No es simplemente `hash(id) % número_de_shards`.

Para productos consultados por ID consideraría `{productoId: "hashed"}`. Favorece
la distribución de identificadores, pero una consulta por rango del ID original
puede necesitar varios shards. Es una propuesta de diseño, no una medición.

Fuentes: [Shard Keys](https://www.mongodb.com/docs/manual/core/sharding-shard-key/) y
[Hashed Sharding](https://www.mongodb.com/docs/manual/core/hashed-sharding/).

## 3. Cómo encuentra el shard correcto

La aplicación se conecta a **mongos**, el router. Los **config servers** guardan
los metadatos de qué rangos pertenecen a cada shard. `mongos` conserva una caché
de esa información y dirige la operación hacia los destinos necesarios.

Una consulta por la shard key completa puede apuntar a un shard concreto. Una
consulta que no permite determinar destinos se envía a varios o todos los shards
y luego combina resultados: scatter/gather. Por eso también diseño la llave a
partir de las consultas, no solo de cómo se distribuyen los documentos.

Fuente: [MongoDB, Routing with mongos](https://www.mongodb.com/docs/manual/core/sharded-cluster-query-router/).

## 4. Qué pasa al agregar un shard

Se registra el shard nuevo y el balancer puede migrar rangos desde shards con más
datos, sujeto a su configuración, zonas y restricciones. No se rehace de golpe
la asignación de todos los documentos con un módulo nuevo.

No hay un número fijo de documentos que siempre se mueva. Depende del volumen,
la distribución y el tamaño de los rangos. El movimiento consume red, disco y
trabajo de coordinación; agregar capacidad no implica tener balance instantáneo.

Fuente: [MongoDB, Sharded Cluster Balancer](https://www.mongodb.com/docs/manual/core/sharding-balancer-administration/).

## 5. Qué hace con un shard caliente

El balanceo por volumen no garantiza balanceo por tráfico. Una llave frecuente
puede recibir muchas operaciones aunque su shard almacene pocos documentos.
También un campo creciente puede concentrar inserciones. Conviene analizar
cardinalidad, frecuencia y monotonicidad al elegir la llave.

Un hash ayuda con valores distintos distribuidos, pero todas las operaciones
sobre **el mismo producto popular** conservan el mismo hash: no se reparten por
magia. Podría revisar una llave compuesta o cambiar el acceso y usar caché para
lecturas, conservando una operación de stock correcta. Cambiar una llave exige
evaluar resharding y su costo; no se arregla siempre moviendo un chunk.

Fuente: [MongoDB, Choose a Shard Key](https://www.mongodb.com/docs/manual/core/sharding-choose-a-shard-key/).

## Ejemplo para explicar el recorrido

`GET productoId=42` → aplicación → mongos → metadatos del rango del hash(42)
→ shard dueño → respuesta. Para buscar todos los productos de una categoría sin
esa llave, mongos podría consultar varios shards.
