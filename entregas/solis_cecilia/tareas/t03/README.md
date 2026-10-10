## Alumna: Cecilia Ximena Solís Cisneros
# T03 Sharding en Redis Cluster

## 1 Qué es y para qué se usa

Elegí Redis Cluster. Redis almacena datos en memoria y se utiliza, por ejemplo, para caché y sesiones de usuarios. Su modo Cluster permite repartir los datos entre varios nodos, en lugar de guardar todo en un solo servidor.

## 2 Cómo reparte los datos

Redis Cluster reparte por hash. Tiene 16 384 espacios llamados hash slots. Para saber en cuál queda una clave, calcula:

`slot = CRC16(clave) % 16384`

Cada nodo primario tiene asignada una parte de esos slots. La aplicación elige el nombre de la clave y Redis calcula su ubicación.

También existen las hash tags: si una clave contiene una parte entre llaves, como `{usuario}`, Redis puede usar esa parte para calcular el slot. Esto permite colocar claves relacionadas juntas. [1]

## 3 Cómo encuentra el shard correcto

Los nodos conocen qué slots pertenecen a cada nodo. El cliente puede obtener ese mapa y guardarlo para enviar las solicitudes directamente al lugar correcto.

Si consulta el nodo equivocado, recibe una respuesta MOVED que indica a cuál debe dirigirse. Durante una migración también puede recibir ASK, que señala una redirección temporal. [2]

## 4 Qué pasa cuando se agrega un shard

Agregar un nodo no significa que inmediatamente reciba datos. Después hay que asignarle slots mediante un proceso de redistribución llamado resharding.

Se trasladan las claves de los slots elegidos, no todos los datos del clúster. La cantidad que se mueve depende de cuántos slots se transfieran y de cuántas claves tengan. Tener la misma cantidad de slots tampoco garantiza ocupar el mismo espacio.

## 5 Qué pasa con un shard caliente

Mover slots permite cambiar qué nodo atiende sus claves. Sin embargo, si el problema es una sola clave muy consultada, moverla solo cambia de lugar la carga: esa clave sigue perteneciendo a un slot.

Para repartir lecturas se pueden utilizar réplicas, si la aplicación acepta que sus datos puedan estar un poco atrasados. Esto no reparte las escrituras de esa clave, que siguen llegando al primario. [3]

Lo que entendí es que agregar nodos ayuda a repartir muchas claves, pero no resuelve automáticamente que todas las solicitudes se concentren en una sola.

## Fuentes

[1] Redis, “Redis cluster specification,” Redis Documentation. [En línea]. Disponible en: https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/. [Consultado: 9-oct-2026].

[2] Redis, “Scale with Redis Cluster,” Redis Documentation. [En línea]. Disponible en: https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/. [Consultado: 9-oct-2026].

[3] Redis, “Scaling Redis: Clustering, Sharding, and Read Replicas Guide,” Redis Tutorials. [En línea]. Disponible en: https://redis.io/tutorials/operate/redis-at-scale/scalability/. [Consultado: 9-oct-2026].
