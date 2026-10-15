# Algoritmos de consenso

**Nombre:** Javier Velasco Pacheco

## Introducción

Los algoritmos de consenso son métodos que permiten que varios nodos de un sistema distribuido lleguen a un acuerdo sobre una misma información. Esto es importante porque los nodos trabajan de manera independiente y pueden haber fallas. Un algoritmo de consenso establece las reglas que deben seguir los nodos para decidir cuál información es válida y mantener el sistema funcionando de manera coordinada. Existen diferentes algoritmos y protocolos de consenso, y cada uno utiliza una forma distinta para lograr este acuerdo. Algunos se enfocan en tolerar fallas de los nodos, mientras que otros también consideran la posibilidad de que un nodo se comporte de manera errónea.

## pBFT (Practical Byzantine Fault Tolerance)

En una red de computadoras, no existe un jefe central que ponga orden. Si una computadora falla y empieza enviando datos falsos a unas partes de la red y verdaderos a otras, el sistema completo puede corromperse. Para resolver este dilema en el mundo real se desarrolló PBFT (Practical Byzantine Fault Tolerance), es un algoritmo que actúa como el mecanismo de seguridad que obliga a las computadoras de una red a realizar votaciones cruzadas estrictas, asegurando que, para que una transacción o verdad sea aceptada, al menos dos terceras partes de la red tengan que ser honestas y estar de acuerdo.

PBFT ofrece una finalidad inmediata, es decir, una vez que una transacción es validada, no puede revertirse. Su funcionamiento utiliza un líder y varias réplicas. El líder puede cambiar mediante diferentes *views* para evitar que un nodo controle permanentemente el proceso.

El funcionamiento de PBFT se divide principalmente en cuatro fases:

- **Pre-prepare**
- **Prepare**
- **Commit**
- **Reply**

En el algoritmo PBFT, el rol de las réplicas se divide entre el nodo principal o líder (*Primary*) y los nodos de respaldo (*Backups*). El nodo líder es el encargado de recibir la solicitud inicial del cliente, asignarle un número de secuencia y difundirla a las demás réplicas durante la fase de pre-preparación (*Pre-prepare*). Por su parte, las réplicas de respaldo (*Backup 1*, *Backup 2* y *Backup 3*) actúan como verificadoras y participan activamente en las fases de votación cruzada (*Prepare* y *Commit*), donde se comunican entre sí para validar la propuesta del líder y garantizar que la gran mayoría de la red esté de acuerdo antes de la transacción y responderle en la fase final de respuesta (*Reply*).

Una de las principales ventajas de PBFT es que puede llegar a un acuerdo incluso cuando algunos nodos fallan o son maliciosos, sin necesitar grandes cantidades de trabajo computacional. Sin embargo, tiene la limitación de que necesita mucha comunicación entre los nodos, por lo que su rendimiento disminuye cuando la red crece. Además, puede ser vulnerable a ataques si una misma entidad controla muchas identidades dentro de la red.

## PoS (Proof of Stake)

Proof of Stake (PoS), es un mecanismo de consenso utilizado principalmente en redes blockchain para validar transacciones y agregar nuevos bloques de manera descentralizada. Su objetivo es que los diferentes participantes de la red puedan ponerse de acuerdo sobre cuál es la información correcta.

PoS utiliza la participación económica de los usuarios como parte de seguridad. Los participantes que desean ayudar a validar la red utilizando una cantidad de la criptomoneda utilizada por esa blockchain.

El funcionamiento de PoS comienza con los participantes que ponen una cantidad de criptomonedas como garantía y se convierten en validadores. Estos validadores ejecutan un programa que les permite comunicarse con los demás participantes de la red y comprobar que las transacciones y los bloques cumplan con las reglas establecidas. Cuando es necesario agregar un nuevo bloque, la red selecciona a uno de los validadores para proponerlo. El bloque contiene un conjunto de transacciones que deben ser revisadas por los demás validadores.

Cuando existe suficiente acuerdo entre los validadores, el bloque puede formar parte de la cadena, así que los nodos mantienen una versión común del estado de la blockchain. Si actúan correctamente, reciben recompensas; si intentan modificar o validar información de manera incorrecta, pueden perder parte de las criptomonedas que dejaron como garantía. De esta forma, el sistema utiliza ganancias y posibles pérdidas económicas para incentivar que los participantes actúen correctamente.

Una de las principales ventajas de PoS es que no necesita realizar grandes cantidades de cálculos para competir por la creación de bloques. Sin embargo, PoS también tiene algunas limitaciones. Para participar directamente como validador es necesario contar con la criptomoneda utilizada.

## Referencias

- Binance Academy. (2026, 19 de mayo). *¿Qué es Proof of Stake (PoS)?* Binance. https://www.binance.com/es/academy/articles/proof-of-stake-explained
- GeeksforGeeks. (2025, 11 de julio). *Practical Byzantine Fault Tolerance (pBFT).* https://www.geeksforgeeks.org/computer-networks/practical-byzantine-fault-tolerancepbft/
- Bitso. (s. f.). *Proof of Stake (PoS): qué es y cómo funciona.* https://bitso.com/mx/glossary/proof-of-stake
- Binance Academy. (2024, 13 de junio). *¿Qué es un algoritmo de consenso?* Binance. https://www.binance.com/es/academy/articles/what-is-a-blockchain-consensus-algorithm
