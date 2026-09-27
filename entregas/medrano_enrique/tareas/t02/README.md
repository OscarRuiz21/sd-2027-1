## T02 - Dos algoritmos de consenso que no son Raft

Este repositorio contiene la investigación sobre dos algoritmos de consenso alternativos a Raft y Paxos, detallando el problema que resuelven y su funcionamiento básico.

## 1. PBFT (Practical Byzantine Fault Tolerance)

Ya que Raft asume que los nodos de un clústher fallan al solo apagarse o desconectarse, es por eso que PBFT se diseño para sobrevivir a un escenario mucho más severo a lo que se llama falla bizantina, por lo que si un nodo sufre o falla por un atque informático o presenta fallas el protocolo funciona correctamente.

PBFT no siempre espera que el líder diga la verdad, sino que procesa rondas de comunicación siguiendo los siguientes pasos, empezando por la petición donde el cliente envía una solicitud al nodo líder, posterior a eso entra en el modo pre-preparación, en donde el primario asigna un orden a la petición y la transmite a todos los demás nodos. Ahora si entra al modo preparación donde los nodos secundarios no lo confriman de forma inmediata, por lo que al recibir el mensaje lo validan y envían una confirmación a los demás nodos. Cada nodo espera recibir confirmaciones idénticas del resto para comprobar que el primario no está enviando versiones distintas a cada participante.

Cómo un penultimo paso sigue el commit, ya que una vez que los nodos recolectan suficientes confirmaciones coincidentes, emite un mensaje de compromiso donde se indica que ya se encuentra listo para ejecutar la acción. Finalmente sucede la ejecución en donde la operación se aprueba únicamente si existe el acuerdo de una súper mayoría. Matemáticamente, la red necesita $3f + 1$ nodos en total para tolerar $f$ nodos maliciosos.

## 2. Proof of Stake (Prueba de Participación - Modelo Ethereum)

Proof of Stake (PoS) está diseñado para redes públicas globales (blockchains) donde cualquier persona puede unirse y no existe confianza previa entre los nodos. Se debe de diferenciar del modelo Bitcoin en donde este logra el conseno exigiendo a las redes de minería gastar electricidad en grandes cantidades, PoS utiliza incentivos económicos directos para asegurar la red.

Esta se divide en 4 pasos:
1. Stake, para tener el derecho de validar transacciones, un usuario debe validar transacciones, un usuario debe depositar y bloquear una cantidad específica de criptomoneda en un contrato inteligente como el 32 ETH conocidos como validadores.

2. Selección pseudoaleatoria, en vez de que todos los nodos compitan simultáneamente, un algoritmo elige de forma aleatoria a un solo validador para que proponga el siguiente bloque de información en un lapso de tiempo específico.

3. Votación, cuando el validador elegido propone un bloque, la red selecciona a un "comité" formado por otros validadores. Ellos revisan la propuesta y, si las transacciones son correctas, emiten un voto a favor.

4. Recompensas y castigos, si el bloque es válido y respaldado por la mayoría, los validadores honestos ganan una recompensa económica, en caso de que el validor sea malisioso o o válido la red detecta la amonimalía y lo castiga destruyendo el dinero que se dejo como garantía.

## Fuentes de consulta.
Castro, M., & Liskov, B. (1999). Practical Byzantine Fault Tolerance. Obtenido de: [URL](https://css.csail.mit.edu/6.824/2014/papers/castro-practicalbft.pdf)

Ethereum Foundation. Proof-of-stake (PoS) & FAQs. Documentación técnica oficial de Ethereum sobre su arquitectura de consenso. Obtenido de: [URL](https://ethereum.org/developers/docs/consensus-mechanisms/pos/)