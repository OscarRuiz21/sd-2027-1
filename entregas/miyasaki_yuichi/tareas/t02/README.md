
## Zab, ZooKeeper Atomic Broadcast

Zab es el protocolo que usa Apache ZooKeeper, un servicio de coordinación para sistemas distribuidos. Su objetivo es que todas las réplicas apliquen los mismos cambios en el mismo orden. Supone que los nodos pueden caerse y recuperarse, pero no que mientan, y con 2f + 1 servidores tolera f caídas. Funciona con un líder y varios seguidores. Cada cambio lleva un identificador llamado zxid, formado por una época que aumenta con cada líder nuevo y un contador que aumenta con cada propuesta. Comparando zxids se obtiene un orden total de todas las operaciones.

Cuando hay un líder nuevo, primero pasa por una fase de descubrimiento, donde los seguidores le reportan su historial y aceptan una época nueva. Después viene la sincronización, en la que el líder iguala el historial de todos antes de aceptar escrituras nuevas, para que nada confirmado se pierda.

En operación normal, el líder manda cada cambio como propuesta, los seguidores lo guardan en disco y responden con un ACK, y cuando una mayoría respondió, el líder manda el commit. Si el líder se cae, el proceso vuelve a empezar. Es muy parecido a Raft, pero separa la sincronización del nuevo líder como una fase propia.

## PBFT, Practical Byzantine Fault Tolerance

PBFT fue propuesto por Castro y Liskov en 1999. A diferencia de Raft y Zab, tolera fallas bizantinas, es decir, nodos que mandan mensajes falsos o maliciosos. Para tolerar f nodos así necesita 3f + 1 réplicas, porque los honestos deben seguir siendo mayoría aunque algunos no respondan.

El sistema avanza en vistas, y en cada una hay un primario que funciona como líder y rota de forma determinista. El cliente manda su petición al primario, y este le asigna un número de secuencia y lo anuncia a todas las réplicas en la fase de pre-prepare.

Como el primario podría mentir, las réplicas no confían en él directamente. En la fase de prepare, cada una reenvía la propuesta a todas las demás, y cuando junta suficientes mensajes iguales sabe que la mayoría honesta vio lo mismo. En la fase de commit repiten el intercambio, y al reunir 2f + 1 confirmaciones ejecutan la operación. El cliente acepta el resultado cuando recibe f + 1 respuestas iguales. Si el primario falla, las réplicas lo detectan por timeout y hacen un cambio de vista para elegir al siguiente. Su principal limitación es que todos hablan con todos, así que el número de mensajes crece mucho y solo funciona bien con pocas decenas de nodos.

## Fuentes

- Junqueira, F., Reed, B. y Serafini, M. 2011. Zab, High-performance broadcast for primary-backup systems. DSN 2011. https://classpages.cselabs.umn.edu/Fall-2017/csci8211/Papers/Distributed%20Systems%20Zab-%20High-performance%20broadcast%20for%20primary-backup%20systems.pdf
- Medeiros, A. 2012. ZooKeeper's atomic broadcast protocol, Theory and practice. Aalto University. http://www.tcs.hut.fi/Studies/T-79.5001/reports/2012-deSouzaMedeiros.pdf
- Castro, M. y Liskov, B. 1999. Practical Byzantine Fault Tolerance. OSDI '99, USENIX. https://www.usenix.org/conference/osdi-99/practical-byzantine-fault-tolerance
- Castro, M. y Liskov, B. 2002. Practical Byzantine Fault Tolerance and Proactive Recovery. ACM Transactions on Computer Systems. http://www.pmg.csail.mit.edu/papers/bft-tocs.pdf
