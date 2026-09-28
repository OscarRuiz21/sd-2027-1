T02 - Algoritmos de consenso 



Hashgraph



Hashgraph es la competencia directa de blockchain y a diferencia de este, no usa bloques ni mineros, ya que organiza las transacciones como una red de eventos dentro de un gráfico acíclico dirigido. Este consenso es la estructuración de dos procesos o mecanismos clave. El primero, de nombre gossip about gossip, manda información entre los nodos de forma aleatoria, dejando un registro de sus predecesores, así hasta tener la completitud de todos los nodos, de esta forma, se logra una propagación de los datos completa en cuestión de segundos. Su segundo mecanismo es la votación virtual, y esta es posible ya que el gossip dejó un registro de cómo se propagó cada transacción dentro de cada nodo, donde este puede calcular por sí mismo, sin que nadie le envíe un voto explícito, si esa transacción ya fue vista por más de 2/3 de los nodos de la red. Si esto se cumple, se considera como si esos nodos hubieran votado a favor, aunque en realidad ningún mensaje de voto fue emitido.



Gracias al conjunto de los procesos anteriores, se logra un sistema con tolerancia a fallos bizantinos, los cuales hacen referencia a cuando un nodo de la red no solo se cae o deja de responder, sino que se comporta de forma maliciosa o arbitraria, pudiendo mandar información falsa, contradictoria o distinta a diferentes nodos a propósito, intentando engañar al resto de la red o sabotear el consenso. De esta forma, mientras menos de 1/3 de los nodos sean maliciosos, la red siempre llega a un consenso correcto.



PBFT (Practical Byzantine Fault Tolerance)



PBFT fue diseñado para hacer que un grupo de copias de servicios o sistemas se comporten como un solo servicio confiable, incluso cuando alguna de ellas falle o no tenga el comportamiento esperado. El funcionamiento principal radica en que las réplicas funcionales en su totalidad nunca deberán separarse del orden en que se ejecutan las peticiones, y de aquí radica una estructura donde tendremos un líder, al que identificaremos como primario, el cual propone el orden de asignación de las peticiones bajo un número de secuencias. Para el resto de las réplicas, las cuales nombraremos backups, no se aceptarán las propuestas arbitrariamente, sino que su proceso de aceptación incluye tres fases: La primera es el pre-prepare, donde se propone el orden, posteriormente le sigue el prepare, donde los backups revisan y reenvían la propuesta hasta que la réplica la respalda y finalmente, llegamos al commit, donde las réplicas confirman que la decisión ya ha quedado ampliamente respaldada, ejecutando la petición y respondiéndole al cliente, quien aprobará los datos si es que se reciben respuestas similares. Para evitar que se aprueben decisiones contradictorias, PBFT pide que cada decisión esté respaldada por un grupo grande de réplicas, esto hará que por lo menos alguna de esas réplicas sea honesta y con ello no se podrían aprobar dos decisiones, ya que esto lo haría algo contradictorio. Si el primario falla o miente, se activa un cambio en el que otra réplica lo reemplaza y continúa el proceso sin perder lo ya avanzado. Finalmente, podemos mencionar que una limitación que tiene este algoritmo es que cada réplica se comunica con las demás, y esto puede ser favorecedor en redes pequeñas, sin embargo, en redes más grandes esto resulta costoso.











FUENTES DE CONSULTA 



“¿Qué es Hashgraph?” Bit2Me Academy. Accedido el 26 de septiembre de 2026. \[En línea]. Disponible: https://academy.bit2me.com/que-es-hashgraph/



I. Inteligente. “Hashgraph, ¿Qué es y cómo funciona?” Inversor Inteligente | Substack. Accedido el 26 de septiembre de 2026. \[En línea]. Disponible: https://inversorinteligente.substack.com/p/hashgraph



“What Is PBFT (Practical Byzantine Fault Tolerance)?” Cube Exchange | Buy Bitcoin \& 1,420+ Crypto — Zero Spread, MPC Custody, No ID required. Accedido el 26 de septiembre de 2026. \[En línea]. Disponible: https://www.cube.exchange/what-is/pbft-practical-byzantine-fault-tolerance





