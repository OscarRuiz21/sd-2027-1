## Viewstamped Replication (VR)

Al igual que en Raft se van recibiendo las peticiones del cliente y las ordena, se espera que las réplicas conozcan la solicitud. Se tiene un nodo primario y los demás son respaldos. Para tolerar fallos $f$ se requiere un total de $2f + 1$ réplicas. Cualquier decisión requiere de la confirmación de $f + 1$ nodos. 

En orden se hace la petición, el primario asigna un número de operación que se agrega a la bitácora, se hace un **PREPARE**, y después se confirma. Entonces se ejecuta y se transmite a los *backups*. Si el nodo primario falla, los *backups* escogen un nuevo líder, tomando al más actualizado; al recuperarse se descargarán los *viewstamp*.

## ZAB (ZooKeeper Atomic Broadcast)

ZooKeeper Atomic Broadcast fue creado para Apache Zookeeper. Se basa en la replicación de árboles de memoria. Se tiene un `zxid` que es el id que sirve para la sincronización, donde se tiene la **época** (que es el periodo de tiempo establecido) y su **counter** (el cual es el contador de la secuencia transaccional, que se reinicia en 0 por cada época). 

Roles de los nodos:
* **Líder:** Lleva todas las peticiones de escritura.
* **Followers:** Replican el historial de transacción y participan en las votaciones de quórum.
* **Observers:** Se actualizan para escalar lecturas (no votan).

Funciona mediante 3 fases:
1. **Discovery:** Se elige el líder, siendo el que tiene el `zxid` más alto.
2. **Synchronization:** Busca sincronizar a la mayoría de los nodos.
3. **Atomic broadcast:** Se busca el $f+1$ de la confirmación de la mayoría para tener en *commits* el mensaje y responder al cliente.

---

### Referencias

* Liskov, B. y Cowling, J. (2012). *Viewstamped Replication Revisited*. MIT-CSAIL-TR-2012-021. [Enlace](https://dspace.mit.edu/handle/1721.1/71763)
* Junqueira, F., Reed, B. y Serafini, M. (2011). *Zab: High-performance broadcast for primary-backup systems*. DSN 2011. [Enlace](https://ieeexplore.ieee.org/document/5958223)
