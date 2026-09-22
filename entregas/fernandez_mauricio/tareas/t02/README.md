PBFT - Practical Byzantine Fault Tolerance

He elegido este primer algoritmo ya que, al investigar sobre el, me he dado cuenta que es bastante similar a Raft, pero con un enfoque diferente.

PBFT significa Practical Byzantine Fault Tolerance, acrónimo del cual la sigla más importante es la correspondiente a “Byzantine”, o bizantino, ya que este algoritmo se basa en la idea de que un servidor o nodo pueda estar enviando información falsa ante la toma de una decisión, siendo que en Raft se considera principalmente un sistema en el que algunos servidores pueden fallar o dejar de estar disponibles. \[1]\[2]

Para solucionar la “discusión” que se da entre este grupo de nodos o servidores, PBFT propone la selección de un nodo que actuará como el “Primary” y los demás como replicas. Este nodo guía entonces las propuestas o decisiones, sin embargo, no solo basta con que este ejecute el rol de líder y tome decisiones por los demás, sino que los nodos réplica se comunican entre ellos para verificar la propuesta realizada por el Primary y llegar a un acuerdo, lo que hace más difícil a este grupo ser burlados por un servidos que “miente” o envía información errónea. \[1]



Para que esta jerarquía y proceso de toma de decisiones funcionen, este algoritmo establece que por cada nodo malicioso “f” que existan, deben haber por lo menos 3f + 1 nodos en total. \[1]



Proof of Work – Bitcoin

Este Segundo algoritmo es bastante diferente en su naturaleza, principalmente por el contexto de uso que se le da. Este se utiliza para llegar a un consenso sobre que bloque de transacciones debe unirse a la cadena o “blockchain”, sin embargo, estos no se tratan de 10 servidores que conocemos, sino de una red abierta y con muchos elementos y participantes de la cual no podemos confiar plenamente. \[3]



Los participantes en este caso reciben el nombre de mineros, los cuales se encargan de construir un bloque y probar una enorme cantidad de valores hasta encontrar uno que produzca un hash que cumpla con la dificultad establecida por la red.” Esto podría verse como una prueba cuyo éxito se determina con los recursos, como el poder y tiempo de procesamiento, utilizados por cada uno de estos mineros para completar la tarea. \[3]\[4]

Cuando uno de ellos encuentra una solución válida, propone el bloque a la red y los demás nodos pueden comprobar que la prueba es correcta. Si es aceptado, se incorpora a la cadena y el minero recibe la recompensa correspondiente por haber realizado este trabajo. \[3]\[4]



Fuentes

\[1] Castro, M., \& Liskov, B. (1999). Practical Byzantine Fault Tolerance. Proceedings of the Third Symposium on Operating Systems Design and Implementation (OSDI '99). USENIX. Artículo original de PBFT - USENIX

\[2] Ongaro, D., \& Ousterhout, J. (2014). In Search of an Understandable Consensus Algorithm. 2014 USENIX Annual Technical Conference. USENIX. Artículo original de Raft - USENIX

\[3] Bitcoin Core. Bitcoin Developer Guide - Block Chain. Documentación de Bitcoin - Block Chain

\[4] Bitcoin Core. Bitcoin Developer Guide - Mining. Documentación de Bitcoin - Mining



