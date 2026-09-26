# Algoritmos de consenso

## ZAB (ZooKeeper Atomic Broadcast)
ZAB es un protocolo de difusión atómica, es decir entrega todos los paquetes de actualización a los nodos o no entrega a ninguno, garantizando que todos los servidores tengan la misma información. 
Está diseñando específicamente para arquitecturas de copia de seguridad primaria (primary-backup), un modelo que consiste en un servidor principal (lider) qprocesa todas las peticiones de escritura de los clientes, y replica estos cambios hacia otros servidores de respaldo (seguidores). Fue creado por Yahoo Research para ser el motor de Apache ZooKeeper, un servicio de coordinación utilizado masivamente en bases de datos distribuidas y ecosistemas Big Data como HBase, Kafka o Hadoop.

### Proceso de elección de líder
En ZAB, los servidores no pueden procesar transacciones sin un líder. Cuando el sistema inicia o el líder actual cae, se activa el protocolo de elección (usualmente Fast Leader Election).

1. Cada nodo mantiene un zxid (ZooKeeper Transaction ID), que es un número de serie único que identifica la última transacción que procesó. Un zxid mayor significa que el nodo tiene los datos más recientes.

2. Al iniciar la elección, cada nodo propone su propio nombre (su ID de servidor) y su zxid a toda la red.

3. Los nodos comparan los zxid recibidos. Si el zxid de otro nodo es mayor que el propio, cambian su voto a favor de ese nodo. (En caso de empate en el zxid, gana el nodo con el ID de servidor más alto).

4. Cuando un nodo recibe los mismos votos de un quórum (la mayoría de la red), ese servidor se declara a sí mismo líder y los demás se convierten en seguidores.

### ¿Cómo funciona?
ZAB garantiza un orden total en la entrega de transacciones  con un comportamiento FIFO (First-In, First-Out) a través del líder establecido. Funciona en tres fases principales:   

1. Descubrimiento (Discovery): Una vez elegido el líder provisional, este recopila el historial de transacciones más reciente de un quórum de seguidores. Esto garantiza que ninguna transacción previamente confirmada (commit) en la red se pierda antes de comenzar a operar.

2. Sincronización (Synchronization): El líder compara su historial definitivo con el de cada seguidor. Si un seguidor está rezagado, el líder le envía las transacciones faltantes para igualar su estado. Una vez que un quórum está 100% sincronizado y consistente, el líder se vuelve oficialmente activo.

3. Difusión (Broadcast): Durante la operación normal, funciona de manera similar a un protocolo de confirmación en dos fases (2PC) simplificado: 

* El líder recibe una solicitud de escritura de un cliente y la emite a los seguidores como una propuesta (Propose)
* Los seguidores guardan la propuesta en disco y responden con un acuse de recibo (ACK)
* Si el líder recibe un quórum de ACKs, asume el éxito de la operación y envía el mensaje final de confirmación (Commit), ordenando a los nodos aplicar el cambio.  

## HotStuff
HotStuff es un algoritmo de Tolerancia a Fallos Bizantinos (BFT). A diferencia de ZAB que solo tolera fallos por caídas de hardware, la tolerancia BFT permite que el sistema alcance un consenso incluso si un porcentaje de los nodos miente, envía datos corruptos de forma intencionada, o actúa bajo el control de atacantes (comportamiento malicioso). Fue introducido en 2019 por investigadores de VMware y revolucionó el consenso para redes federadas y bases de datos descentralizadas.

### Proceso de elección y cambio de vista (Pacemaker)
HotStuff divide el tiempo de operación en períodos llamados Vistas (Views). Cada vista tiene asignado un líder predeterminado de manera rotativa (por ejemplo, en un esquema de Round-Robin o por turnos). El algoritmo delega la elección a un componente independiente llamado Pacemaker o Marcapasos:

* No hay una fase de votación compleja para elegir líder. Si los nodos detectan que el líder actual de la vista no responde a tiempo (timeout), el marcapasos dicta que la vista actual caducó.

* Los nodos envían un mensaje a la red declarando el salto a la siguiente vista.
* El nodo al que le toca el turno en la nueva vista asume el liderazgo inmediatamente y retoma el progreso basándose en el estado validado de la vista anterior.

### ¿Cómo funciona?
Los algoritmos BFT clásicos sufren de una complejidad de comunicación cuadrática $\mathcal{O}(n^2)$ cuando el líder falla, lo que ralentiza enormemente la red a medida que crecen los nodos. HotStuff soluciona esto introduciendo un cambio de vista lineal $\mathcal{O}(n)$ utilizando criptografía avanzada (firmas umbral) y una estructura de confirmación en cadena.
Cada bloque transita por un pipeline estructurado en cuatro fases: 

1. Preparación (Prepare): El líder propone un bloque de transacciones. Los nodos verifican su validez técnica mediante una regla local estricta (SafeNode) para evitar conflictos con transacciones pasadas. Si es correcto, no envían el mensaje a todos los nodos, sino que envían una firma criptográfica parcial únicamente al líder. 
2. Pre-Commit: El líder recopila estas firmas parciales y, mediante criptografía, las combina en un único Certificado de Quórum (QC) (una prueba matemática innegable de que la mayoría aprobó la preparación). Difunde este QC a la red. Los nodos guardan este estado y emiten una nueva firma apoyando el QC. 
3. Commit: El líder repite el proceso: recolecta las firmas de la fase anterior y genera un nuevo QC de confirmación. Al recibirlo, los nodos se "bloquean" criptográficamente en esta propuesta, garantizando matemáticamente que nunca votarán por propuestas futuras que intenten contradecir o revertir este bloque.   
4. Decisión (Decide): Una vez validado el último QC por el líder, se envía la instrucción final. El líder ordena a los nodos que apliquen la transacción permanentemente a sus registros locales y avancen de vista de manera definitiva.

Referencias Bibliográficas
* Junqueira, F. P., Reed, B. C., y Serafini, M. (2011). Zab: High-performance broadcast for primary-backup systems. En 2011 IEEE/IFIP 41st International Conference on Dependable Systems & Networks (DSN), pp. 395-406. IEEE.
* Cachin, C., Guerraoui, R., & Rodrigues, L. (2011). Introduction to Reliable and Secure Distributed Programming. Springer Science & Business Media.   
* Yin, M., Malkhi, D., Reiter, M. K., Gueta, G. G., y Abraham, I. (2019). HotStuff: BFT Consensus with Linearity and Responsiveness. En Proceedings of the 2019 ACM Symposium on Principles of Distributed Computing (PODC '19), pp. 347–356. Association for Computing Machinery.   