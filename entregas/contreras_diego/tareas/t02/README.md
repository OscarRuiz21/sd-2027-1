# Tarea 02: Dos algoritmos de consenso que no son Raft

**1. Zab (ZooKeeper Atomic Broadcast)**

**¿Cómo funciona?**
Zab es un algoritmo de consenso orientado a sistemas tolerantes a fallos por parada o caída de nodos, creado para gestionar la replicación de estados en Apache ZooKeeper. A diferencia de Raft, que prioriza la concordancia continua de un registro de comandos mediante logs, Zab enfoca su operación en garantizar una difusión atómica de transacciones ordenadas sobre una estructura de datos compartida en memoria.

El proceso inicia con la fase de recuperación y elección de líder. Cuando el sistema arranca o el nodo dominante falla, los servidores realizan una votación para seleccionar un nuevo líder, eligiendo al candidato que contenga el identificador de transacción más reciente. Inmediatamente después, el nuevo líder fuerza un proceso de sincronización con todos los seguidores para asegurar que cada participante comparta exactamente el mismo historial antes de aceptar nuevas operaciones de los usuarios.

Una vez estabilizado el liderazgo, el sistema pasa a la fase de difusión atómica. Cuando un cliente envía una petición de escritura, esta es procesada por el líder, quien le asigna un identificador secuencial único. El líder distribuye una propuesta a todos los seguidores mediante un esquema de confirmación en dos etapas sin cancelación. Cada nodo seguidor registra la propuesta en almacenamiento persistente y responde con una confirmación. Al recibir la respuesta positiva de una mayoría absoluta de la red, el líder ordena la ejecución definitiva del cambio a todos los nodos para mantener la consistencia estricta.

**2. PBFT (Practical Byzantine Fault Tolerance)**

**¿Cómo funciona?**
Practical Byzantine Fault Tolerance es un protocolo desarrollado para resolver el problema del consenso distribuido en entornos propensos a fallos bizantinos, donde los nodos no solo pueden dejar de responder, sino también enviar información falsa, contradictoria o actuar de forma maliciosa. Para mantener la seguridad y garantizar que todos los nodos honestos coincidan en el mismo estado, la red requiere contar con una cantidad total de participantes mayor o igual a tres veces el número máximo de nodos corruptos permitidos más uno.

El algoritmo organiza su funcionamiento en fases iterativas llamadas vistas, gestionadas por un nodo primario en cada turno, mientras que el resto opera como nodos secundarios. Cuando un cliente transmite una solicitud al nodo primario, este le asigna un número correlativo y emite un mensaje de pre-preparación a toda la red. Al recibir dicha notificación, cada secundario valida la autenticidad del mensaje y retransmite un mensaje de preparación a todos los demás participantes. Un nodo confirma que la solicitud está preparada cuando reúne las confirmaciones de una supermayoría de la red, asegurando un orden global sin ambigüedades.

En la etapa final de compromiso, cada nodo transmite una última notificación a los demás miembros tras alcanzar el estado preparado. Cuando un participante recopila suficientes confirmaciones válidas de distintos nodos, efectúa la transacción localmente y responde al cliente. El cliente considera válida y definitiva la operación en el momento en que recibe respuestas idénticas de una cantidad de nodos superior al margen de fallos tolerados. En caso de que el primario actúe maliciosamente o falle, los secundarios activan un temporizador para destituirlo mediante una votación de cambio de vista.

**Fuentes consultadas**

1. GeeksforGeeks. (2023). Zookeeper Atomic Broadcast (ZAB). GeeksforGeeks Documentation. https://www.geeksforgeeks.org/zookeeper-atomic-broadcast-zab/
2. GeeksforGeeks. (2024). Practical Byzantine Fault Tolerance (pBFT). GeeksforGeeks Distributed Systems. https://www.geeksforgeeks.org/practical-byzantine-fault-tolerance-pbft/
3. Medium / Towards Data Science. (2021). Understanding Byzantine Fault Tolerance and PBFT. https://towardsdatascience.com/understanding-byzantine-fault-tolerance-and-pbft-4d378087265
