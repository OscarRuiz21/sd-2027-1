# T02. Dos algoritmos de consenso que no son Raft

*En la clase vimos cómo Raft pone de acuerdo a varios nodos con un líder, latidos y mayoría. Hay otros algoritmos que resuelven el mismo problema de otra forma.*

*Qué haces. Eliges dos algoritmos o protocolos de consenso que no sean Raft ni Paxos y escribes, para cada uno, un resumen de cómo funciona, con tus palabras. Al final, las fuentes que usaste.*

*Algunos para elegir: Zab (ZooKeeper), Viewstamped Replication, PBFT, Tendermint, HotStuff, Proof of Work (Bitcoin), Proof of Stake (Ethereum).*


## Zab (ZooKeeper)

El algoritmo Zookeper Atomic Broadcast se enfoca en asegurar la fiabilidad y consistencia entre servidores. Se utiliza en Apache Zookeper, un sistema encargado de implementar coordinación distribuida: Servicios de nombres, gestión de configuración, sincronización y servicios de grupo con una infraestructura centralizada.

Se utiliza para que múltiples servidores se coordinen y tengan el mismo orden de operaciones, incluso cuando se segmentan las redes o los servidores fallan.

Tiene dos modos:
+ Elección de líder: Escoge a un servidor para que gestione las actualizaciones de estado a sus seguidores. Procesa las peticiones de escritura y que se apliquen en el orden correcto. Si falla se elige otro en un proceso tolerante a fallas y los seguidores que también fallaron pueden volver a unirse al sistema cuando se recupere sin perder datos. Además, deben omitirse las propuestas anteriores que no se hayan confirmado.
+ Broadcast atómico: Los broadcasts que actualizan a los seguidores y que aplican las actualizaciones en el mismo orden.

De esta manera, se asegura que todos los cambios se propaguen y apliquen consistentemente en todos los nodos, en el mismo orden para evitar condiciones de carrera. Cada cambio de estado es incremental con respecto al estado anterior.


+ Cada servidor seguidor que recibe una solicitud de un cliente debe reenviar dicha solicitud al líder para que sea propuesta.
+ El líder recibe mensajes de los seguidores y propone valores a su quórum a través de canales FIFO.
+ Una vez que recibe la confirmación de un quórum, difunde un mensaje de confirmación de compromiso (*commit*) a sus seguidores.
+ Los seguidores entregan un mensaje tras haber recibido un mensaje de confirmación de compromiso por parte del líder.

### Fase 1. Fase de elección del líder 

Como ya se mencionó, ocurre cuando un líder falla o cuando el sistema se está iniciando. Se elige por medio de un quorum, y cada servidor vota por el que consideran adecuado utilizando una técnica parecida a raft que combina el número de epoch y el ID de transacción. El elegido obtiene el epoch del seguidor con el número más alto e incrementa el valor para evitar que varios servidores propongan el mismo número. 

### Fase 2. Fase de descubrimiento

El líder envía un mensaje de discovery a los seguidores con el estado actual y el ID de su última transacción. Ya que tiene el más reciente, garantiza que no se pierda ninguna transacción ya confimada y descarta las que no llegaron a serlo.

### Fase 3. Fase de sincronización

Con el estado más reciente, envía las transacciones a los seguidores que les falte para mantener la sincronización. Estos lo aplican a sus registros locales y mandan una confirmación de vuelta. 

### Fase 4. Fase de difusión (Atomic broadcast)

El líder procesa las solicitudes de escritura entrantes y las difunde a todos los seguidores.
Para cada solicitud de escritura, el líder crea una propuesta (un mensaje que contiene la transacción) y la envía a los seguidores. Mientras que, los seguidores registran la propuesta y envían una confirmación (ACK).

Debe haber un quórum de seguidores que confirme la propuesta, y así el líder lo confirma con un commit y notifica a los seguidores para que sea aplicada. 

### Fase 5. Fase de recuperación (Gestión de fallos)

Ante el fallo del nodo primario, los procesos ejecutan un protocolo de recuperación tanto para acordar un estado común consistente antes de reanudar la operación normal como para establecer un nuevo nodo primario que difunda los cambios de estado; es decir, vuelve a la fase 1.

## 
Su desventaja es que está limitado a ZooKeeper y no ha sido extraído a una base de datos independiente. Además, su dependencia a un líder que gestione el sistema lo hace menos de propósito general.



## Proof of Work (Bitcoin)

El algoritmo Proof of Work nace de la necesidad de realizar y confirmar transacciones económico sin tener que ser validados por instituciones financieras. Se usó por primera vez para Bitcoin, la primera criptomoneda, y antes era usada por Ethereum, pero después fue cambiado a Proof of Stake.

La tecnología Blockchain se diferencia por ser decentralizada y porque utiliza bloques de datos que están enlazados por medio de criptografía. Cada bloque contiene:
+ Datos del bloque, como la lista de transacciones o la dificultad.
+ El hash del bloque previo.
+ Nonce encontrado.

El algoritmo de consenso es necesario para tomar decisiones entre los nodos de una red y llegar a un acuerdo cuando se va a unir un nuevo bloque de datos al blockchain. Además de evitar ataques de DoS y spam, bajo el supuesto de que no todos los nodos son confiable.

En este algoritmo entonces hace que ciertos participantes (miners) resuelvan un hash de 64 dígitos hexadecimales utilizando una cierta cantidad de recursos computacionales, y encontrar el nonce (mining). La forma más eficiente de lograrlo es mediante la prueba y error. Después de que la primera persona lo resuelve, los demás nodos verifican la operación con el nonce encontrado. Si es validado puede sumar un bloque al blockchain y se es recompensado.

Esto es efectivo hasta cierto punto debido a que utiliza las propiedades de hash, en particular, SHA256: dificil de resolver pero facil de verificar.


# Referencias
1. Nehal Navlani. (2025, julio 23). Zab Algorithm in Distributed Systems. Geeksforgeeks. Disponible en: [https://www.geeksforgeeks.org/system-design/zab-algorithm-in-distributed-systems/](https://www.geeksforgeeks.org/system-design/zab-algorithm-in-distributed-systems/)

2. Zookeper (2026). Apache Zookeper. Disponible en: [https://zookeeper.apache.org/](https://zookeeper.apache.org/)

3. Aditya Shete. (2022, mayo 25). The Zab Algorithm. Medium. Disponible en: [https://medium.com/@adityashete009/the-zab-algorithm-502781c54498](https://medium.com/@adityashete009/the-zab-algorithm-502781c54498)

4. Flavio P. Junqueira, Benjamin C. Reed, and Marco Serafini. (2011). Zab: High-performance broadcast for
primary-backup systems. Yahoo! Research. IEEE 978-1-4244-9233-6. Disponible en: [https://classpages.cselabs.umn.edu/Fall-2017/csci8211/Papers/Distributed%20Systems%20Zab-%20High-performance%20broadcast%20for%20primary-backup%20systems.pdf](https://classpages.cselabs.umn.edu/Fall-2017/csci8211/Papers/Distributed%20Systems%20Zab-%20High-performance%20broadcast%20for%20primary-backup%20systems.pdf)

5. Alibaba Clouder. (2019, abril 11). A Brief Analysis of Consensus Protocol: From Logical Clock to Raft. Alibaba Cloud Community. Disponible en: [https://www.alibabacloud.com/blog/a-brief-analysis-of-consensus-protocol-from-logical-clock-to-raft_594675](https://www.alibabacloud.com/blog/a-brief-analysis-of-consensus-protocol-from-logical-clock-to-raft_594675)

6. Lyle Daly, (2025, octubre 13). What Is Proof of Work (PoW) in Crypto? The Motley Fool. DIsponible en: [https://www.fool.com/terms/p/proof-of-work/](https://www.fool.com/terms/p/proof-of-work/)

7. Zain ul Abadin, Madiha Syed. (2021, julio). A Pattern for Proof of Work Consensus Algorithm in Blockchain. EuroPLoP'21. DOI: https://doi.org/10.1145/3489449.3489994. Disponible en: [https://dl.acm.org/doi/fullHtml/10.1145/3489449.3489994](https://dl.acm.org/doi/fullHtml/10.1145/3489449.3489994)

8. 0xIchigo. (2023, septiembre 21). Proof of History, Proof of Stake, Proof of Work - Explained. Helius. Disponible en: [https://www.helius.dev/blog/proof-of-history-proof-of-stake-proof-of-work-explained](https://www.helius.dev/blog/proof-of-history-proof-of-stake-proof-of-work-explained)

9. Aleksandr Gladkikh. (2025, enero 3). The Proof-of-Work (PoW) Mechanism in Blockchain. Medium. Disponible en: [https://towardsdev.com/the-proof-of-work-pow-mechanism-in-blockchain-6a49196cab75](https://towardsdev.com/the-proof-of-work-pow-mechanism-in-blockchain-6a49196cab75)

10. wackerow. (2026). Proof-of-work (PoW). Ethereum Documentation. Disponible en: [https://ethereum.org/developers/docs/consensus-mechanisms/pow/](https://ethereum.org/developers/docs/consensus-mechanisms/pow/)
