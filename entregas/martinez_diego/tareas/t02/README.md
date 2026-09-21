\# T02 - Algoritmos de consenso



\## 1. Practical Byzantine Fault Tolerance (PBFT)



PBFT (Practical Byzantine Fault Tolerance) es un algoritmo de consenso diseñado para sistemas distribuidos donde algunos nodos pueden fallar o incluso comportarse de manera maliciosa. A diferencia de algoritmos como Raft, PBFT considera los llamados fallos bizantinos, donde un nodo puede enviar información incorrecta o diferente al resto.



El algoritmo trabaja con un grupo conocido de nodos. Uno de ellos funciona como nodo primario o líder y los demás como réplicas. Para tolerar f nodos defectuosos, normalmente se necesitan al menos 3f + 1 nodos en total. Por ejemplo, para tolerar un nodo malicioso se necesitan al menos cuatro nodos.



\### ¿Cómo funciona?



El proceso normal de PBFT puede resumirse en tres etapas principales:



1\. \*\*Pre-prepare:\*\* un cliente envía una solicitud al nodo primario. El primario asigna un número de secuencia y comunica la solicitud al resto de los nodos.



2\. \*\*Prepare:\*\* las réplicas reciben la solicitud, verifican que sea válida y envían mensajes a los demás nodos indicando que están de acuerdo con la propuesta.



3\. \*\*Commit:\*\* después de recibir suficientes mensajes de acuerdo, los nodos confirman la operación y la ejecutan. Finalmente, el cliente recibe respuestas de varios nodos y puede considerar aceptado el resultado cuando obtiene suficientes respuestas coincidentes.



Si el nodo primario deja de funcionar correctamente o no permite que el sistema avance, PBFT puede realizar un cambio de vista (view change) para seleccionar otro nodo como primario y continuar el proceso.



\### Ejemplo sencillo



Supongamos que existen cuatro servidores que deben ponerse de acuerdo sobre una operación. Si uno de ellos se comporta incorrectamente, los otros tres pueden intercambiar mensajes y llegar a un acuerdo sobre cuál es el resultado correcto. Esto permite que el sistema continúe funcionando aunque exista un nodo defectuoso.



PBFT es especialmente útil cuando los participantes son conocidos, por ejemplo, en determinados sistemas empresariales y redes blockchain permisionadas.



\## 2. Proof of Stake (PoS)



Proof of Stake (PoS), o Prueba de Participación, es un mecanismo de consenso utilizado principalmente en redes blockchain. En lugar de hacer que los participantes compitan realizando cálculos computacionalmente costosos, como sucede en Proof of Work, PoS utiliza la cantidad de criptomonedas que un participante tiene bloqueadas como una forma de participación en el consenso.



Los participantes que quieren ayudar a validar bloques normalmente deben depositar o bloquear una cantidad de fondos, proceso conocido como staking. A partir de este mecanismo se seleccionan validadores que pueden proponer o participar en la validación de nuevos bloques.



\### ¿Cómo funciona?



De manera simplificada:



1\. Los participantes depositan una cantidad de criptomonedas para participar como validadores.



2\. El protocolo selecciona uno o varios validadores para proponer el siguiente bloque.



3\. Los demás validadores revisan las transacciones y votan o validan la propuesta.



4\. Si se cumplen las condiciones de consenso, el nuevo bloque se agrega a la cadena.



5\. Los validadores reciben recompensas por participar correctamente. Dependiendo del protocolo, también pueden existir penalizaciones para quienes actúen de manera incorrecta.



La idea principal es que la participación económica hace que los validadores tengan algo que perder si intentan manipular el sistema.



\### Ejemplo sencillo



Supongamos que cinco personas participan en una red y cada una bloquea cierta cantidad de monedas para ser validador. El sistema selecciona a uno de ellos para proponer un bloque y los demás verifican que las transacciones sean correctas. Si el bloque cumple las reglas, se acepta y continúa la cadena.



A diferencia de Proof of Work, PoS no depende de una competencia constante de poder computacional para seleccionar al participante que propone el bloque, por lo que puede reducir considerablemente el consumo energético asociado al consenso.



\## Comparación



| Característica | PBFT | Proof of Stake |

|---|---|---|

| Tipo de sistema | Sistemas distribuidos y redes permisionadas | Principalmente blockchain |

| Participantes | Normalmente conocidos | Pueden ser participantes de una red blockchain |

| Tolerancia a fallos | Tolera fallos bizantinos | Depende del protocolo y sus reglas |

| Forma de consenso | Intercambio de mensajes y votaciones | Participación económica y validación |

| Líder/proponente | Tiene un primario por vista | Se seleccionan validadores/proponentes |

| Consumo computacional | Generalmente bajo comparado con PoW | Menor que PoW |

| Ejemplo de uso | Redes empresariales y blockchain permisionada | Redes blockchain basadas en participación |



\## Conclusión



PBFT y Proof of Stake utilizan enfoques diferentes para conseguir que varios participantes lleguen a un acuerdo. PBFT se basa principalmente en la comunicación y votación entre nodos conocidos y está diseñado para soportar incluso nodos que pueden comportarse de manera maliciosa. Proof of Stake utiliza la participación económica de los validadores para determinar quién participa en la creación y validación de bloques.



En ambos casos, el objetivo es que los participantes de una red distribuida mantengan un estado común y eviten que diferentes nodos terminen aceptando información incompatible.



