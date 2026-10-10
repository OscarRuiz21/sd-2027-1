# T03 · Una implementación real de sharding

**Alumno:** Fernando Reyes Vázquez

## Redis Cluster

### ¿Qué es y para qué se usa?

Redis es un sistema de almacenamiento de datos que trabaja principalmente en memoria y utiliza una estructura de llave-valor. Puede utilizarse como base de datos, caché, intermediario de mensajes y en aplicaciones donde se necesita acceder a los datos de manera rápida.

Redis Cluster es la solución de Redis para distribuir información entre varios nodos. Su objetivo principal es permitir el **escalamiento horizontal**, es decir, aumentar la capacidad del sistema agregando más máquinas en lugar de depender de una sola máquina cada vez más potente.

Una diferencia que vimos en clase es que **replicar y partir los datos no es lo mismo**. Cuando se replica, varias máquinas conservan copias de la misma información. En el sharding, cada máquina almacena una parte diferente de los datos; por eso los shards pueden entenderse como particiones de la información.

Redis Cluster utiliza esta idea para repartir las llaves entre diferentes nodos del clúster, evitando que todos los datos tengan que almacenarse en una sola instancia.

---

### ¿Cómo reparte los datos?

Redis Cluster reparte los datos utilizando **hash**.

Para hacer esta distribución se necesita primero una **llave de partición**, tal como trabajamos en clase. A partir de esa llave se puede aplicar alguna regla para decidir a qué partición corresponde cada dato, por ejemplo por rango o por hash.

En Redis Cluster, las llaves se distribuyen entre **16 384 hash slots**, numerados del 0 al 16 383. Para saber a qué slot corresponde una llave, Redis realiza el siguiente cálculo:

```text
CRC16(llave) mod 16384
```

El resultado indica el hash slot al que pertenece la llave. Después, los distintos nodos que forman el clúster son responsables de determinados grupos de slots.

Por ejemplo, si una llave llamada `usuario:Ana` obtiene un hash slot que pertenece al nodo 2, el dato será almacenado en ese nodo. Otra llave puede producir un resultado diferente y terminar almacenada en otro nodo.

En este caso, la aplicación define las llaves con las que guarda la información, mientras que Redis se encarga de calcular el hash y determinar automáticamente el slot correspondiente.

La ventaja de este método es que las llaves pueden quedar distribuidas entre diferentes nodos sin tener que indicar manualmente en qué máquina se debe almacenar cada una.

---

### ¿Cómo encuentra el shard correcto cuando llega una consulta?

Al repartir la información entre varias máquinas aparece otro de los problemas que vimos en clase: **alguien tiene que saber dónde está cada cosa**.

Redis Cluster no utiliza un único servidor central encargado de guardar todo el mapa. Los nodos conocen cómo están distribuidos los hash slots dentro del clúster y los clientes compatibles con Redis Cluster también pueden mantener información sobre qué nodo es responsable de cada grupo de slots.

Cuando se realiza una consulta, el cliente puede calcular el hash slot correspondiente a la llave y, utilizando esa información, enviar la petición al nodo que debe contener el dato.

También puede ocurrir que la petición sea enviada a un nodo incorrecto. En ese caso, Redis responde con una redirección llamada **`MOVED`**, indicando qué nodo es responsable de ese slot. El cliente puede utilizar esta respuesta para dirigirse al nodo correcto y actualizar su información sobre la distribución del clúster.

De esta forma, el cliente no necesita conocer manualmente dónde se encuentra cada dato, sino que utiliza los hash slots y la información proporcionada por el clúster para encontrar el nodo correspondiente.

---

### ¿Qué pasa cuando se agrega un shard?

Cuando se agrega un nuevo nodo a Redis Cluster, los datos existentes no se copian completamente hacia él.

Al principio, el nuevo nodo todavía no tiene hash slots asignados. Para que realmente participe en la distribución de los datos es necesario realizar un proceso llamado **resharding**.

Este proceso consiste en mover determinados hash slots desde los nodos existentes hacia el nuevo nodo. Junto con esos slots también se trasladan las llaves que pertenecen a ellos.

Por ejemplo, si inicialmente existen dos nodos y cada uno tiene aproximadamente la mitad de los 16 384 slots, al agregar un tercer nodo se pueden mover algunos slots de los primeros dos hacia el nuevo nodo para repartir mejor la información.

Esto significa que **no es necesario mover todos los datos del clúster**. Solamente se trasladan los datos correspondientes a los slots que se decidió cambiar de nodo.

Redis permite realizar este proceso mientras el clúster sigue funcionando, lo que facilita aumentar su capacidad sin tener que reconstruir por completo la distribución de los datos.

---

### ¿Qué hace con un shard caliente?

Un **shard caliente** aparece cuando uno de los nodos recibe una cantidad mucho mayor de trabajo que los demás.

Un punto importante que vimos en clase es que repartir los datos de manera uniforme no garantiza repartir también el trabajo. Con hash se pueden distribuir correctamente muchas llaves, pero algunas pueden utilizarse mucho más que otras.

Por ejemplo, varios nodos podrían almacenar aproximadamente la misma cantidad de llaves, pero si la mayoría de las consultas se realizan sobre datos almacenados en uno de ellos, ese nodo tendrá una carga mucho mayor.

En Redis Cluster se pueden mover hash slots entre nodos mediante **resharding** para intentar distribuir mejor los datos y la carga.

Sin embargo, esto tiene una limitación. Si el problema es una sola llave que recibe una cantidad muy grande de consultas, mover su slot únicamente cambia el nodo que soporta esa carga. La llave sigue perteneciendo a un solo hash slot, por lo que Redis no la divide automáticamente entre varios shards.

Por eso, además de repartir los datos, también es importante considerar cómo serán utilizadas las llaves. **El hash puede repartir bien las cuentas, pero no necesariamente el trabajo**.

---

## Conclusión

Redis Cluster es una implementación real de sharding que utiliza una estrategia basada en hash. Cada llave se transforma mediante `CRC16` y se asigna a uno de los **16 384 hash slots** disponibles. Estos slots se distribuyen entre los diferentes nodos que forman el clúster.

Cuando llega una consulta, el cliente puede utilizar la distribución de los slots para determinar qué nodo debe atenderla. Si la petición llega a un nodo incorrecto, Redis puede responder con una redirección `MOVED` indicando dónde se encuentra el slot correspondiente.

Cuando se agrega un nuevo nodo no es necesario mover todos los datos. Mediante el resharding se trasladan solamente algunos hash slots y las llaves que pertenecen a ellos.

Finalmente, Redis Cluster también permite ver que una distribución uniforme de los datos no significa necesariamente una distribución uniforme del trabajo. Si ciertas llaves reciben muchas más consultas que otras, un nodo puede convertirse en un shard caliente aunque almacene una cantidad de información parecida a los demás.

---

## Fuentes

- Redis. (s. f.). *Redis Open Source*. Redis Documentation.  
  https://redis.io/docs/latest/get-started/

- Redis. (s. f.). *Redis Cluster specification*. Redis Documentation.  
  https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/

- Redis. (s. f.). *Scale with Redis Cluster*. Redis Documentation.  
  https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/

- Microsoft. (s. f.). *Escalado de una instancia de Azure Cache for Redis*. Microsoft Learn.  
  https://learn.microsoft.com/es-es/azure/azure-cache-for-redis/cache-how-to-scale

- Google Cloud. (s. f.). *Prácticas recomendadas generales para Memorystore for Redis Cluster*. Google Cloud.  
  https://cloud.google.com/memorystore/docs/cluster/general-best-practices?hl=es-419