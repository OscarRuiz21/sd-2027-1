# T02 · Dos algoritmos de consenso que no son Raft

En sistemas distribuidos, el consenso es el problema fundamental de lograr que un conjunto de nodos independientes acuerde un mismo estado o secuencia de operaciones, aun frente a caídas de servidores, desconexiones o retrasos en la red. Aunque algoritmos como Paxos y Raft son ampliamente conocidos para entornos con fallos por detención (*crash-fault tolerance* o CFT), existen otros protocolos diseñados con supuestos, arquitecturas y garantías distintas.

En este documento se analizan dos algoritmos de consenso fundamentales:
1. **Zab (ZooKeeper Atomic Broadcast)**: un protocolo orientado a sistemas de réplica primario-respaldo de alto rendimiento con tolerancia a caídas.
2. **PBFT (Practical Byzantine Fault Tolerance)**: el protocolo pionero para alcanzar consenso en redes asíncronas tolerando nodos arbitrarios o maliciosos (*Byzantine fault tolerance* o BFT).

---

## Zab (ZooKeeper Atomic Broadcast)

### ¿Qué es?

**Zab** es un protocolo de difusión atómica diseñado específicamente para **Apache ZooKeeper**, el servicio distribuido de coordinación utilizado por sistemas como Apache Kafka, Hadoop y HBase. Fue desarrollado por Flavio Junqueira, Benjamin Reed y Marco Serafini en Yahoo! Research.

A diferencia de protocolos de consenso genéricos concebidos como máquinas de estados replicadas simétricas (como Paxos multi-decreto), Zab resuelve el problema de mantener réplicas sincronizadas bajo un modelo **primario-respaldo (*primary-backup*)**. En este modelo, el sistema está fuertemente optimizado para cargas de trabajo con una proporción masiva de lecturas frente a un volumen menor de escrituras:

* **Lecturas ultra-rápidas locales**: Cualquier réplica del clúster puede responder lecturas de inmediato consultando su copia local en memoria, sin consultar al líder ni generar tráfico de quórum.
* **Escrituras con orden total centralizado**: Todas las operaciones que modifican el estado (crear, modificar o borrar nodos en el árbol de ZooKeeper) se canalizan obligatoriamente hacia un único nodo líder, quien se encarga de serializarlas y difundirlas a todos los seguidores (*followers*).

El problema principal que resuelve Zab es garantizar que todas las réplicas apliquen exactamente las mismas actualizaciones en el mismo orden estricto, preservando el orden causal de las peticiones del cliente (*FIFO client order*) y asegurando que ninguna actualización confirmada se pierda si el líder se apaga repentinamente.

### ¿Cómo funciona?

Zab opera mediante el concepto de **épocas (*epochs*)**. Una época representa el periodo de mandato de un líder específico. Si el líder actual falla y se elige a otro, el clúster incrementa el número de época.

Para gobernar el orden, Zab define el **`zxid` (ZooKeeper Transaction ID)**, un entero de 64 bits compuesto por dos partes:
* **Los 32 bits más significativos**: Representan el número de la época (`epoch`).
* **Los 32 bits menos significativos**: Son un contador monótonamente creciente asignado a cada transacción dentro de esa época.

Gracias al `zxid`, comparar dos transacciones es trivial: una transacción con mayor época es necesariamente más reciente; si pertenecen a la misma época, el contador menor precede al mayor.

#### Fases principales del protocolo

Zab alterna entre dos modos de operación:

1. **Fase de Difusión Atómica (*Atomic Broadcast*)**:  
   Es el modo de operación normal del sistema. Cuando el líder recibe una petición de escritura de un cliente:
   * **Propuesta (*Proposal*)**: El líder emite una propuesta con un `zxid` correlativo y la envía a todos los seguidores a través de canales de red punto a punto ordenados (usualmente conexiones TCP con colas FIFO).
   * **Acuse de recibo (*ACK*)**: Cada seguidor recibe la propuesta en orden FIFO, la persiste en su registro de transacciones en disco y envía un mensaje `ACK` al líder. A diferencia de un protocolo *Two-Phase Commit* (2PC) tradicional donde los nodos pueden votar "abort", en Zab los seguidores no abortan peticiones válidas; solo responden con un ACK.
   * **Compromiso (*Commit*)**: En cuanto el líder reúne una mayoría simple de ACKs (un quórum de $\lfloor N/2 \rfloor + 1$ nodos, contándose a sí mismo), la transacción se considera comprometida. El líder aplica el cambio a su árbol en memoria y envía un mensaje `COMMIT` a los seguidores para que estos también actualicen su copia local y respondan al cliente.

2. **Fase de Recuperación y Sincronización (*Crash-Recovery & Sync*)**:  
   Se activa cuando el clúster arranca o cuando el líder cae. Durante esta fase se suspende la difusión atómica, se elige un nuevo líder y este sincroniza el registro de los seguidores antes de aceptar nuevas operaciones.

### ¿Qué ocurre si falla el líder?

Cuando los seguidores dejan de recibir los mensajes periódicos de latido (*heartbeats*) del líder, se dispara un tiempo de espera (*timeout*) y todos los nodos pasan al estado de **Elección (*Election*)**.

Zab impone dos garantías críticas durante la recuperación para asegurar la consistencia del sistema:
1. **Nunca perder una transacción confirmada**: Toda propuesta que fue comprometida (*committed*) por el líder previo debe sobrevivir y aplicarse en todos los nodos operativos.
2. **Descartar propuestas no comprometidas**: Cualquier propuesta que el líder anterior haya alcanzado a generar localmente pero que no logró quórum de ACKs antes de caer debe eliminarse, evitando bifurcaciones o estados fantasma.

Para lograr esto de forma elegante y rápida, el algoritmo de elección de Zab (denominado *Fast Leader Election*) aplica una regla de voto directa: **se elige como nuevo líder al nodo que tenga el mayor `zxid` de todo el quórum**. Como ese nodo posee la transacción más avanzada que alcanzó a persistirse, se garantiza matemáticamente que incluye todas las transacciones que pudieron haber alcanzado quórum.

Una vez electo:
* El nuevo líder incrementa el número de época en su nuevo `zxid`.
* Pasa a la fase de **Sincronización (*Sync*)**: Compara su registro de transacciones con el de cada seguidor. Si un seguidor se quedó atrás por desconexión momentánea, el líder le envía las transacciones faltantes (`DIFF`); si el seguidor tiene transacciones huérfanas de una época anterior no comprometida, se le ordena truncar su registro (`TRUNC`); y si el seguidor está demasiado desactualizado, se le transfiere una instantánea completa de la memoria (`SNAP`).
* Solo cuando una mayoría de seguidores confirma haber completado la sincronización, el líder declara inaugurada la nueva época y comienza a procesar nuevas peticiones de escritura.

---

## PBFT (Practical Byzantine Fault Tolerance)

### ¿Qué es?

Publicado en 1999 por **Miguel Castro y Barbara Liskov** en el simposio OSDI, **PBFT** representó un hito en la computación distribuida. Hasta ese momento, los algoritmos que toleraban fallos bizantinos se consideraban puramente teóricos o demasiado lentos para implementaciones reales porque requerían sincronía estricta o tenían una complejidad de mensajes exponencial.

PBFT fue el primer protocolo en demostrar que era viable construir un servicio de máquina de estados replicada tolerante a fallos bizantinos en **redes asíncronas** (como Internet o redes de área local, donde los mensajes pueden retrasarse, duplicarse o llegar desordenados) con un costo de comunicación polinomial de $O(N^2)$ por solicitud.

El problema que resuelve es el acuerdo unánime y la consistencia de réplicas en entornos donde una fracción de los participantes puede comportarse de manera completamente impredecible, corrupta o adversarial.

### Tolerancia a fallos

Para comprender PBFT es indispensable distinguir entre los dos grandes modelos de fallo:

* **Fallos por caída (*Crash Faults*)**: El modelo clásico asumido por Zab, Raft o Paxos. Aquí un nodo defectuoso simplemente deja de responder, se reinicia o sufre desconexión. Sin embargo, se confía plenamente en que los nodos vivos dicen la verdad y ejecutan el protocolo de forma honesta.
* **Fallos bizantinos (*Byzantine Faults*)**: El modelo más severo posible en sistemas distribuidos (derivado del clásico dilema de los Generales Bizantinos de Lamport, Shostak y Pease, 1982). Aquí los nodos afectados pueden:
  * Omitir respuestas deliberadamente.
  * Modificar o falsificar mensajes.
  * Enviar mensajes contradictorios a diferentes compañeros (por ejemplo, votar "sí" ante el nodo A y "no" ante el nodo B).
  * Coludirse entre sí para intentar revertir o alterar transacciones.
  * Comportarse de forma errática debido a corrupción silenciosa de memoria, bugs en software o un ataque informático activo.

Un sistema tolerante a fallos bizantinos garantiza **seguridad (*safety*)** (las réplicas honestas nunca acuerdan valores distintos) y **vivacidad (*liveness*)** (el sistema no se detiene indefinidamente y sigue procesando operaciones legítimas), incluso cuando parte de sus nodos estén activamente comprometidos.

#### ¿Cuántos nodos se necesitan en PBFT?

Para tolerar hasta $f$ nodos bizantinos simultáneos, PBFT requiere un tamaño mínimo de clúster de:

$$N \ge 3f + 1$$

Por ejemplo:
* Para tolerar $f = 1$ nodo malicioso, se requieren al menos $N = 4$ nodos.
* Para tolerar $f = 2$ nodos maliciosos, se requieren al menos $N = 7$ nodos.

**¿Por qué $3f + 1$? (Demostración intuitiva):**
1. En una red asíncrona, no es posible distinguir si un nodo tardó en responder porque la red está lenta o porque falló y es bizantino. Por lo tanto, para no bloquearse indefinidamente esperando a $f$ nodos que tal vez nunca respondan, el sistema debe poder avanzar en cuanto recibe respuestas de **$N - f$** nodos.
2. Sin embargo, en el peor de los casos, los $f$ nodos que no respondieron eran nodos perfectamente honestos que sufrieron retrasos en la red, lo que significa que dentro de los $N - f$ nodos que sí respondieron a tiempo se encuentran los **$f$ nodos bizantinos mintiendo o enviando votos contradictorios**.
3. Para que los votos de los nodos honestos superen en número y neutralicen el sabotaje de los $f$ nodos bizantinos, la cantidad de nodos honestos dentro del quórum que respondió ($(N - f) - f$) debe ser estrictamente mayor que $f$:
   $$(N - f) - f > f \implies N - 2f > f \implies N > 3f$$
   Al ser $N$ un número entero de nodos, el mínimo posible es $N = 3f + 1$.

### ¿Cómo funciona?

En PBFT, las réplicas operan dentro de una sucesión de configuraciones denominadas **vistas (*views*)**. En cada vista existe un nodo designado como **Primario (*Primary*)** y los restantes nodos actúan como **Respaldos (*Backups*)**.

Todos los mensajes intercambiados están autenticados mediante firmas digitales o códigos de autenticación de mensajes (*MAC*) sobre el resumen criptográfico (*digest*) de la solicitud, lo que impide que un nodo intermedio altere el contenido de un mensaje ajeno.

El ciclo para procesar una solicitud de un cliente atraviesa tres fases:

```
Cliente      Primario           Backup 1          Backup 2          Backup 3
   |            |                  |                 |                 |
   |--REQUEST-->|                  |                 |                 |
   |            |---PRE-PREPARE--->|---------------->|---------------->|
   |            |<------PREPARE--->|<----PREPARE---->|<----PREPARE---->|  (todos contra todos)
   |            |<-------COMMIT--->|<-----COMMIT---->|<-----COMMIT---->|  (todos contra todos)
   |            |                  |                 |                 |
   |<--REPLY----|------------------|-----------------|-----------------|  (espera f + 1 respuestas)
```

1. **Pre-preparación (*Pre-Prepare*)**:
   * El cliente envía una petición $m$ al primario.
   * El primario le asigna un número de secuencia monótono $n$, genera un mensaje `PRE-PREPARE` que incluye la vista actual $v$, el número $n$ y el digest de la petición, y lo envía a todos los backups.
   * Esto establece el orden propuesto para la solicitud dentro de la vista.

2. **Preparación (*Prepare*)**:
   * Cada backup valida el mensaje del primario (firma válida, vista correcta y que no haya aceptado previamente otro mensaje con el mismo $n$ y diferente digest).
   * Si es correcto, el backup difunde un mensaje `PREPARE` a **todos** los demás nodos del clúster.
   * Cada nodo recopila los mensajes recibidos. Cuando un nodo acumula el `PRE-PREPARE` y $2f$ mensajes `PREPARE` coincidentes de nodos distintos, conforma un **certificado de preparación (*prepared certificate*)**. En este punto, el clúster ha acordado formalmente el orden de la petición dentro de esa vista.

3. **Compromiso (*Commit*)**:
   * En cuanto un nodo tiene su certificado de preparación, difunde un mensaje `COMMIT` a todos los demás participantes.
   * Cada réplica espera reunir $2f + 1$ mensajes `COMMIT` válidos (de nodos distintos). Al juntarlos, conforma un **certificado de compromiso (*commit certificate*)**.
   * Esto garantiza que un quórum suficiente de nodos no defectuosos sabe que la petición está preparada, lo que hace irrevocable la decisión incluso si ocurre un cambio de vista.
   * La réplica ejecuta localmente la operación en su máquina de estados y envía el resultado (`REPLY`) directamente al cliente.

**Respuesta al cliente**: El cliente da por completada la operación en cuanto recibe **$f + 1$ respuestas idénticas y válidas** provenientes de réplicas distintas. Puesto que a lo sumo $f$ nodos pueden ser maliciosos, tener $f + 1$ respuestas idénticas asegura que al menos un nodo honesto ejecutó el cómputo y el resultado es fidedigno.

### ¿Qué ocurre si algunos nodos fallan o actúan incorrectamente?

El protocolo contempla dos situaciones según quién falle:

1. **Si falla o delinque un nodo de respaldo (*Backup*)**:  
   Si un backup se cae, envía datos corruptos o intenta difundir digests falsificados, el impacto en el sistema es nulo. Las réplicas descartan los mensajes con firmas inválidas y, como el quórum requerido en cada fase es de $2f + 1$ votos sobre un total de $3f + 1$, la ausencia o la mentira de hasta $f$ backups no impide que los $2f + 1$ nodos honestos alcancen el certificado y completen la transacción.

2. **Si falla o delinque el Primario (*Primary*)**:  
   El primario representa el rol más crítico. Un primario malicioso podría intentar:
   * Asignar el mismo número de secuencia a dos peticiones distintas (*equivocation*).
   * Enviar propuestas sólo a un subconjunto de nodos para dividirlos.
   * Silenciar el sistema negándose a proponer peticiones de ciertos clientes.

   Para neutralizar esto, los backups inician un temporizador en cuanto reciben una petición de un cliente. Si el temporizador expira sin que la petición se haya ejecutado (porque el primario no emitió el `PRE-PREPARE` o demoró la fase), los backups desconfían del primario e inician el procedimiento de **Cambio de Vista (*View Change*)**:
   * Cada backup envía a todos un mensaje `VIEW-CHANGE` solicitando avanzar a la vista $v + 1$, adjuntando los certificados de las peticiones preparadas en la vista anterior como evidencia criptográfica.
   * El nodo correspondiente a la nueva vista (determinado de manera determinista como $(v + 1) \pmod N$) espera recibir $2f + 1$ mensajes `VIEW-CHANGE`.
   * Con esta evidencia, el nuevo primario construye un mensaje `NEW-VIEW`, garantiza la continuidad de todas las peticiones preparadas previamente (impidiendo que se altere el pasado acordado) y reanuda el procesamiento normal de transacciones.

---

## Cuadro comparativo de conceptos

| Característica | Zab (ZooKeeper Atomic Broadcast) | PBFT (Practical Byzantine Fault Tolerance) |
| :--- | :--- | :--- |
| **Modelo de fallos** | Caídas e interrupciones (*Crash-Fault Tolerant* - CFT) | Arbitrarios y maliciosos (*Byzantine Fault Tolerant* - BFT) |
| **Confianza en el líder** | Alta: los seguidores asumen que el líder no miente ni falsifica mensajes. | Nula: los respaldos verifican con criptografía cada acción del primario. |
| **Nodos necesarios** | Mayoría simple: $N \ge 2f + 1$ (ej. 3 nodos para tolerar 1 fallo) | Supermayoría: $N \ge 3f + 1$ (ej. 4 nodos para tolerar 1 nodo malicioso) |
| **Quórum por operación** | $\lfloor N/2 \rfloor + 1$ votos (mayoría simple) | $2f + 1$ votos (dos tercios de la red) |
| **Topología de mensajes** | Estrella: Líder $\leftrightarrow$ Seguidores ($O(N)$ por propuesta) | Punto a punto: Todos contra todos en Prepare y Commit ($O(N^2)$) |
| **Optimización clave** | Lecturas locales en memoria sin pasar por consenso | Consenso BFT determinista y práctico en redes asíncronas |
| **Caso de uso típico** | Coordinación interna de infraestructura (ZooKeeper, Kafka, Hadoop) | Redes descentralizadas, consorcios y sistemas con desconfianza mutua |

---

## Fuentes

1. **Junqueira, F. P., Reed, B. C., & Serafini, M. (2011).** *Zab: High-performance broadcast for primary-backup systems*. In 2011 IEEE/IFIP 41st International Conference on Dependable Systems & Networks (DSN) (pp. 245-256). IEEE.  
   Disponible en: https://ieeexplore.ieee.org/document/5958223 / https://www.datadoghq.com/pdf/zab.interpreted-systems.or-something.pdf
2. **Apache ZooKeeper Documentation.** *ZooKeeper Internals - Zab: ZooKeeper Atomic Broadcast protocol*. Apache Software Foundation.  
   Disponible en: https://zookeeper.apache.org/doc/current/zookeeperInternals.html
3. **Castro, M., & Liskov, B. (1999).** *Practical Byzantine Fault Tolerance*. In Proceedings of the Third Symposium on Operating Systems Design and Implementation (OSDI '99) (Vol. 99, pp. 173-186). USENIX Association.  
   Disponible en: https://www.usenix.org/conference/osdi-99/practical-byzantine-fault-tolerance
4. **Castro, M., & Liskov, B. (2002).** *Practical Byzantine fault tolerance and proactive recovery*. ACM Transactions on Computer Systems (TOCS), 20(4), 398-461.  
   Disponible en: https://dl.acm.org/doi/10.1145/571637.571640
5. **Lamport, L., Shostak, R., & Pease, M. (1982).** *The Byzantine Generals Problem*. ACM Transactions on Programming Languages and Systems (TOPLAS), 4(3), 382-401.  
   Disponible en: https://dl.acm.org/doi/10.1145/357172.357176
