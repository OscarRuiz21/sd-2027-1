# T02: Dos algoritmos de consenso que no son Raft

## 1. PBFT (Practical Byzantine Fault Tolerance)

**El problema que resuelve:** 
En clase vimos que el algoritmo RAFT resolvía el problema de que un grupo de máquinas se pusieran de acuerdo sobre un valor o estado, a pesar de que unas se apagaran o la red fallara, sin embargo también asumía que ningún nodo mentiría, es aquí donde entra el algoritmo PBFT. 
El problema que resuelve de igual forma es garantintizar que, incluso con la presencia de fallos, nodos vulderados que puedan mandar información falsa o comportamiento malicioso dentro del sistema, este pueda seguir funcionando de forma adecuada y logre alcanzar el consenso.

**Cómo funciona:**
Este algoritmo funciona a través de un protocolo de tres fases de verificación intensiva:
1. **Pre-prepare:** El nodo líder (llamado primario) recibe una petición del cliente y la transmite a todos los demás nodos (réplicas).
2. **Prepare:** Los nodos reciben el mensaje y lo reenvían a *todos* los demás nodos para decir "recibí esto del líder, ¿tú también?".
3. **Commit:** Si un nodo ve que una súper mayoría está de acuerdo en que el mensaje es el mismo, lo da por válido y lo ejecuta.

El gran problema de este algoritmo es su escalabilidad, ya que a medida que queremos tolerar nodos maliciosos o defectuosos, la cantidad de mensajes aumentarán de manera exponencial.

**Nota:** la regla que dictamina el número de nodos a usar dependiendo de los nodos defectuosos que se busca tolerar está impuesta por la regla matemática 3f+1, donde f es el número máximo de nodos defectuosos. 
 
---

## 2. Zab (ZooKeeper Atomic Broadcast)

**El problema que resuelve:**
Zab es el protocolo de consenso diseñado específicamente para Apache ZooKeeper, su propósito es garantizar la difusión atomómica en un orden estricto en todas las computadoras para garantizar la coordinación y coherencia en un sistema. Al igual que RAFT depende de elegir a un nodo líder que garantice que todas las operaciones se realicen en el mismo orden en todas las máquinas.

**Cómo funciona:**
Funciona básicamente en dos modos principales.
1. **Elección de Líder:** En este punto se elige un único servidor como líder, aquel nodo que tenga el historial de transacciones más actualizada será el nodo líder. Este ya es el encargado de realizar las actualizaciones de estados.
2. **Difusión atómica:** Es aquí donde el nodo líder transmite las actualizaciones a todos los nodos, asegurándose que se transmitan en el mismo orden, manteniendo de esta forma la coherencia en el sistema.
**Nota:**  Al igual que en Raft, Zab requiere de una mayoría absoluta (quórum) para operar y confirmar transacciones, evitando el problema de tener "dos verdades" si la red se parte.

---

### Fuentes
* Nolan, S. (2018, 18 de noviembre). pBFT: Comprensión del algoritmo de consenso. Coinmonks. https://medium.com/coinmonks/pbft-understanding-the-algorithm-b7a7869650ae
* Tolerancia práctica a faltas bizantinas (PBFT) - Crypto.com. (s. f.). Crypto.com. https://crypto.com/es/glossary/practical-byzantine-fault-tolerance-pbft
* Yao, Z., Fang, Y., Pan, H., Wang, X., & Si, X. (2024). A secure and highly efficient blockchain PBFT consensus algorithm for microgrid power trading. Scientific Reports, 14(1), 8300. https://doi.org/10.1038/s41598-024-58505-w
* GeeksforGeeks. (2025, 23 julio). Zab Algorithm in Distributed Systems. GeeksforGeeks. https://www.geeksforgeeks.org/system-design/zab-algorithm-in-distributed-systems/
* 
