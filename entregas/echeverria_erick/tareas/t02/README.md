# Tarea 2 - Dos algoritmos de consenso que no son Raft
### Alumno: Echeverria Goicochea Erick Isaac

## Proof of Stake (Ethereum)

En este algoritmo de consenso, los participantes deben bloquear una cantidad de sus propias monedas para poder actuar como validadores, a mayor cantidad apostada, mayor es la probabilidad de ser elegido. La red selecciona de forma aleatoria a un validador para que organice las transacciones pendientes y proponga un nuevo bloque a la cadena. Una vez que este bloque es propuesto, los demás validadores de la red se encargan de verificarlo para comprobar que las transacciones sean válidas, que el bloque cumpla con las reglas del protocolo y que no exista ningún conflicto con el estado anterior de la blockchain.

En sistemas como Ethereum, estos validadores realizan lo que se conoce como attestations, es decir, emiten un voto o certificación digital confirmando que el bloque propuesto es correcto, logrando así que diferentes participantes distribuidos lleguen a un acuerdo y el bloque quede finalizado en la cadena. Si el validador y los certificadores actúan de forma honesta, son recompensados con nuevas monedas o comisiones. Sin embargo, si un validador intenta hacer trampa aprobando operaciones falsas o actuando de manera negligente, la red lo detecta a través de la revisión de los demás participantes y le aplica una sanción llamada Slashing, la cual consiste en quitarle o destruir parte o la totalidad de las monedas que ingresó como garantía.

A diferencia de Proof of Work, en Proof of Stake no existe un acertijo matemático complejo que resolver. Por esta razón, el cálculo de validación es prácticamente instantáneo, se resuelve muy rápido y reduce el consumo de energía de la red.

## ZAB (ZooKeeper Atomic Broadcast)

En este protocolo de consenso, se trabaja bajo un esquema de líder y seguidores. Esto significa que dentro de todo el grupo de servidores se elige a una sola computadora como líder, la cual es la única encargada de recibir y gestionar las solicitudes de cambio o escritura de datos, mientras que las demás computadoras actúan como seguidoras recibiendo y aplicando esos mismos cambios.

El proceso funciona principalmente en dos etapas. En la primera etapa, llamada elección de líder, si el sistema apenas se está iniciando o si el líder actual sufre una falla y se desconecta, todos los servidores entran en un proceso de votación rápida para elegir a un nuevo líder, asegurándose de escoger al que tenga la información más reciente. Durante este tiempo de transición, la red no acepta nuevos cambios para evitar incoherencias.

Una vez que hay un líder confirmado, se pasa a la segunda etapa, conocida como difusión atómica. Cuando llega una nueva transacción, el líder les propone el cambio a los seguidores. Cada seguidor revisa la propuesta y responde con un mensaje de confirmación. En cuanto la mayoría de los servidores está de acuerdo, el líder da por aprobada la transacción y les ordena a todos guardar el cambio en su registro.

Para que no haya confusiones con el orden de los datos, ZAB le asigna a cada transacción un identificador único conocido como ZXID. Este código incluye el número de periodo del líder actual y un número secuencial para cada operación, lo que garantiza que, incluso si un líder se cae y entra uno nuevo, todas las transacciones se apliquen en la secuencia correcta sin saltearse ni duplicar nada.

### Referencias

Frankenfield, J. (2023, 13 de diciembre). Proof of stake (PoS). Investopedia. [https://www.investopedia.com/terms/p/proof-stake-pos.asp](https://www.investopedia.com/terms/p/proof-stake-pos.asp)

GeeksforGeeks. (2024, 18 de junio). ZAB algorithm in distributed systems. [https://www.geeksforgeeks.org/system-design/zab-algorithm-in-distributed-systems/](https://www.geeksforgeeks.org/system-design/zab-algorithm-in-distributed-systems/)

Simply Explained. (2021, 21 de mayo). Proof of stake explained [Video]. YouTube. [https://www.youtube.com/watch?v=x83EVUZ_EWo](https://www.youtube.com/watch?v=x83EVUZ_EWo)