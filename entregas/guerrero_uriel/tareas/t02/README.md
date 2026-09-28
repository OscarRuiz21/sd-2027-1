T02 · Dos algoritmos de consenso que no son Raft

HotStuff

HotStuff es un algoritmo de consenso diseñado para sistemas distribuidos donde algunos de los participantes pueden fallar o incluso comportarse de manera maliciosa. Es un algoritmo tolerante a fallos bizantinos, por lo que busca que los nodos honestos puedan ponerse de acuerdo sobre el mismo orden de las operaciones aunque algunos nodos no funcionen correctamente.

El funcionamiento general de HotStuff es el siguiente:

Los nodos de la red participan en rondas, llamadas vistas. En cada vista existe un líder encargado de proponer un nuevo bloque.

El líder recibe las operaciones o transacciones que deben procesarse y propone un bloque al resto de los nodos.

Los demás nodos reciben la propuesta y verifican que sea válida. Si están de acuerdo, envían una confirmación al líder.

Cuando se obtiene suficiente apoyo de los nodos, se forma un certificado de confianza que demuestra que una cantidad suficiente de participantes está de acuerdo con la propuesta.

El protocolo utiliza varias fases de confirmación antes de considerar que un bloque puede quedar comprometido. Esto ayuda a evitar que diferentes nodos honestos terminen aceptando valores diferentes.

Si el líder deja de responder o se comporta de manera incorrecta, los nodos pueden cambiar a una nueva vista y elegir otro líder para continuar el proceso.

Una característica importante de HotStuff es que utiliza una estructura llamada Quorum Certificate (QC), que representa el acuerdo de una cantidad suficiente de nodos sobre una propuesta.

HotStuff puede tolerar hasta f nodos con comportamiento bizantino cuando existen al menos 3f + 1 nodos en el sistema. Por ejemplo, si se quieren tolerar 1 nodo defectuoso, se necesitan al menos 4 nodos en total.

Este algoritmo fue diseñado para reducir la complejidad de comunicación que tenían algunos protocolos anteriores de tolerancia a fallos bizantinos. Además, su estructura facilita el cambio de líder cuando el actual deja de funcionar correctamente.

PBFT (Practical Byzantine Fault Tolerance)

PBFT es otro algoritmo de consenso para sistemas distribuidos que pueden presentar fallos bizantinos. Fue diseñado para permitir que un grupo de servidores llegue a un acuerdo aunque algunos de ellos puedan fallar o enviar información incorrecta.

En PBFT existe un nodo principal, llamado primary, y varios nodos secundarios, llamados replicas.

El funcionamiento básico es el siguiente:

Un cliente envía una solicitud al nodo principal.

El nodo principal recibe la solicitud y propone que los nodos procesen esa operación.

Los nodos secundarios reciben la propuesta y verifican que sea válida.

Después comienza una serie de fases de comunicación entre los nodos. Estas fases permiten que las réplicas comparen sus mensajes y comprueben que existe suficiente acuerdo sobre la operación.

Cuando una cantidad suficiente de nodos confirma la misma operación, esta puede ejecutarse y el resultado se considera aceptado.

Los nodos envían posteriormente una respuesta al cliente. El cliente puede considerar válida la operación cuando recibe suficientes respuestas consistentes.

Si el nodo principal falla o se comporta incorrectamente, los nodos pueden iniciar un cambio de vista para elegir un nuevo nodo principal.

PBFT también puede tolerar hasta f nodos con fallos bizantinos cuando existen al menos 3f + 1 nodos en el sistema.

Una de sus principales características es que los nodos necesitan intercambiar una gran cantidad de mensajes para llegar al acuerdo. Esto puede funcionar bien en redes con una cantidad limitada de participantes, pero la comunicación aumenta considerablemente cuando el número de nodos crece.

Diferencia principal

| Aspecto                  | HotStuff                                                            | PBFT                                                          |
| ------------------------ | ------------------------------------------------------------------- | ------------------------------------------------------------- |
| Tipo de consenso         | Tolerancia a fallos bizantinos                                      | Tolerancia a fallos bizantinos                                |
| Participantes            | Nodos validadores                                                   | Réplicas                                                      |
| Líder                    | Un líder por vista                                                  | Un primary por vista                                          |
| Comunicación             | Estructurada alrededor de Quorum Certificates                       | Varias fases de comunicación entre réplicas                   |
| Tolerancia a fallos      | Hasta f nodos con 3f + 1 nodos                                      | Hasta f nodos con 3f + 1 nodos                                |
| Cambio de líder          | Cambio de vista                                                     | Cambio de vista                                               |
| Principal característica | Reduce la complejidad de comunicación y facilita el cambio de líder | Proporciona consenso mediante varias fases de comunicación    |
| Uso                      | Sistemas distribuidos y blockchains                                 | Sistemas distribuidos con un número limitado de participantes |

En resumen, tanto HotStuff como PBFT buscan que un conjunto de nodos llegue al mismo acuerdo incluso cuando algunos participantes pueden fallar o comportarse de manera maliciosa.

La principal diferencia está en la forma en que organizan la comunicación y el acuerdo entre los nodos. PBFT utiliza varias fases de comunicación entre las réplicas, mientras que HotStuff organiza el consenso mediante vistas, líderes y Quorum Certificates.

Ambos algoritmos pertenecen a los protocolos de tolerancia a fallos bizantinos y pueden tolerar hasta f nodos defectuosos cuando la red cuenta con al menos 3f + 1 participantes.

Fuentes usadas

Castro, M., & Liskov, B. (1999). Practical Byzantine Fault Tolerance. Proceedings of the Third Symposium on Operating Systems Design and Implementation (OSDI). https://pmg.csail.mit.edu/papers/osdi99.pdf

Yin, M., Malkhi, D., Reiter, M. K., Gueta, G. G., & Abraham, I. (2019). HotStuff: BFT Consensus in the Lens of Blockchain. Proceedings of the 2019 ACM Symposium on Principles of Distributed Computing. https://arxiv.org/abs/1803.05069

HotStuff. (s. f.). HotStuff: BFT Consensus in the Lens of Blockchain. https://hotstuff.org/

Castro, M., & Liskov, B. (1999). Practical Byzantine Fault Tolerance. MIT. https://pmg.csail.mit.edu/papers/osdi99.pdf

