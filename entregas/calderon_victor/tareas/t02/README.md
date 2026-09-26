# T02 · Dos algoritmos de consenso que no son Raft

**Nombre:** Victor Emiliano Calderón Gutiérrez  

---
Para esta tarea, me basé en algunos de los protocolos que se mencionaron en la descripción de la tarea, y los elegí porque fueron los que más me llamaron la atención por el nombre, además de que yo ya había oído del PoW de Bitcoin, entonces me interesó saber más sobre esos dos protocolos.

## 1. Zab (ZooKeeper)

Por lo que estuve investigando, el protocolo de Zab funciona de forma similar a Raft (que fue el que vimos en clase y sobre lo que trató una de las lecturas): ambos modelos se basan en un líder que se encarga de mandar información a los seguidores y cuentan con un proceso de elección de líder. Lo que vi que cambia es la forma en que esta información se confirma (*commit*). En Zab se genera un `zxid` que identifica de manera única y secuencial cada operación (compuesto por una época y un contador). Para poder hacer commit de una propuesta, el líder necesita recibir la confirmación de una mayoría simple de nodos (un quórum $\lfloor N/2 \rfloor + 1$, donde el propio líder cuenta). En cambio, a diferencia de lo que a veces se piensa, en Raft tampoco se necesita la confirmación de todos los seguidores (ambos usan quórum de mayoría), pero Raft reconcilia los registros directamente con el RPC `AppendEntries` sobreescribiendo conflictos, mientras que Zab depende de canales de red TCP con orden FIFO estricto y tiene fases explícitas de sincronización (`DIFF`, `TRUNC` y `SNAP`). Además, una de las desventajas de Zab es que es un protocolo muy acoplado a la implementación interna de Apache ZooKeeper, mientras que Raft se diseñó para ser más entendible y modular.

## 2. Proof of Work (Bitcoin)

Este es un protocolo mucho más interesante. Previamente había tenido la oportunidad de oír sobre el protocolo de PoW de Bitcoin, pero no sabía mucho sobre cómo funcionaba. Lo que estuve investigando me hizo entender mucho mejor su funcionamiento. Se trata de un acertijo criptográfico donde se toman los datos del bloque (transacciones, hash del bloque anterior) y mediante iteraciones de fuerza bruta se busca encontrar un número (*nonce*) que cumpla con la condición de que el doble hash SHA-256 sea menor o igual a un objetivo (*target*, es decir, que empiece con cierta cantidad de ceros). Los participantes (mineros) compiten para encontrar este número y quien lo encuentre primero tiene el derecho de añadir el siguiente bloque a la cadena y recibe una recompensa económica que es cuando se dan bitcoins. Si hay desacuerdos o ramas paralelas (bifurcaciones), la red siempre sigue la cadena con mayor trabajo acumulado (la regla de la cadena más pesada). La diferencia principal con Raft es que PoW es abierto y tolera fallas bizantinas (es decir, nodos maliciosos que intentan engañar o hacer doble gasto, no solo caídas de nodos). Además, mientras que en Raft el acuerdo es determinista e inmediato en cuanto responde la mayoría, en Bitcoin el consenso es probabilístico y requiere esperar varias confirmaciones para asegurar que una transacción sea irreversible.

## 3. Fuentes

- Ongaro, D., & Ousterhout, J. (2014). *In Search of an Understandable Consensus Algorithm (Raft)*. USENIX ATC.
- Junqueira, F. P., Reed, B. C., & Serafini, M. (2011). *Zab: High-performance broadcast for primary-backup systems*. IEEE DSN.
- Nakamoto, S. (2008). *Bitcoin: A Peer-to-Peer Electronic Cash System*.
- Material y notas de la sesión S05 de Sistemas Distribuidos (Consenso y Raft).