## ¿Que es un algoritmo de consenso? ##
Un algoritmo de consenso es un proceso que se utiliza para lograr un acuerdo sobre un único valor de datos entre procesos o sistemas distribuidos. Estos algoritmos están diseñados para garantizar la fiabilidad en una red con múltiples usuarios o nodos. 
En general, los algoritmos de consenso son fundamentales en los sistemas distribuidos ya que permiten que un grupo de computadoras independientes en un sistema distribuido se pongan de acuerdo sobre un único valor o estado, incluso cuando algunos equipos fallan o la red es inestable.
Para dar una mejor explicacion sobre que hacen los algoritmos de consenso, en esta tarea dare un resumen de como y que hacen los algoritmos de PoW y PBFT

## PoW - Proof of Work (Prueba de Trabajo) ##
Este algoritmo exige a los participantes en la red que aporten una prueba computacional de que se ha realizado trabajo antes de poder añadir un nuevo bloque a la cadena de bloques.
En el proceso PoW, los participantes de la red, conocidos como mineros, compiten para resolver acertijos matemáticos. La resolución de estos acertijos requiere un esfuerzo y potencia de cálculo. El primer minero que resuelve con éxito el enigma transmite la solución a la red para su verificación. Si la solución es correcta, el minero obtiene el derecho a añadir un nuevo bloque de transacciones a la cadena de bloques.
Por lo que investigue, este algoritmo es utilizado para las criptomonedas como bitcoin.

## PBFT - Practical Byzantine Fault Tolerance (Tolerancia Práctica a Fallos Bizantinos) ##
El algoritmo PBFT funciona organizando los nodos de la red en una secuencia donde uno actúa como nodo primario (líder) y los demás como réplicas, buscando llegar a un acuerdo mediante una regla de mayoría en rondas llamadas "vistas".
Su funcionamiento paso a paso se resume en las siguientes cuatro fases:
1. Solicitud: Un cliente envía una petición de operación al nodo primario.
2. Distribución: El nodo primario transmite la solicitud a todas las réplicas simultáneamente.
3. Ejecución y respuesta: Las réplicas procesan la solicitud y envían sus respuestas directamente al cliente.
4. Verificación: El cliente espera a recibir respuestas coincidentes de la mayoría de las réplicas.

Ademas, para evitar errores este algoritmo usa algunas reglas: una es para la tolerancia a fallos, la cual requiere que menos de un tercio de los nodos en la red sean maliciosos; la otra regla es que la sustitucion de los lideres solo se realizara si el nodo que se consideraba "lider" no responde en un tiempo determinado.  





## REFERENCIAS: ##
- GeeksforGeeks. (18 de marzo de 2024). Consensus algorithms in distributed system. https://www.geeksforgeeks.org / operating-systems/consensus-algorithms-in-distributed-system/
- TechTarget. (s. f.). What is a consensus algorithm? https://www.techtarget.com/whatis/definition/consensus-algorithm
- Crypto.com. (s. f.). Tolerancia práctica a faltas bizantinas (PBFT). Glosario. https://crypto.com/es/glossary/practical-byzantine-fault-tolerance-pbft
- Trakx. (s. f.). Prueba de Trabajo (PoW). Glosario. https://trakx.io/es/glosario/pow/
