## ¿Que es un algoritmo de consenso? ##
Un algoritmo de consenso es un proceso que se utiliza para lograr un acuerdo sobre un único valor de datos entre procesos o sistemas distribuidos. Estos algoritmos están diseñados para garantizar la fiabilidad en una red con múltiples usuarios o nodos. 
En general, los algoritmos de consenso son fundamentales en los sistemas distribuidos ya que permiten que un grupo de computadoras independientes en un sistema distribuido se pongan de acuerdo sobre un único valor o estado, incluso cuando algunos equipos fallan o la red es inestable.
Para dar una mejor explicacion sobre que hacen los algoritmos de consenso, en esta tarea dare un resumen de como y que hacen los algoritmos de PoW y PBFT

## PoW - Proof of Work (Prueba de Trabajo) ##
Este algoritmo exige a los participantes en la red que aporten una prueba computacional de que se ha realizado trabajo antes de poder añadir un nuevo bloque a la cadena de bloques.
En el proceso PoW, los participantes de la red, conocidos como mineros, compiten para resolver acertijos matemáticos. La resolución de estos acertijos requiere un esfuerzo y potencia de cálculo. El primer minero que resuelve con éxito el enigma transmite la solución a la red para su verificación. Si la solución es correcta, el minero obtiene el derecho a añadir un nuevo bloque de transacciones a la cadena de bloques.
Por lo que investigue, este algoritmo es utilizado para las criptomonedas como bitcoin.

## PBFT - Practical Byzantine Fault Tolerance (Tolerancia Práctica a Fallos Bizantinos) ##

El algoritmo PBFT, introducido por Miguel Castro y Barbara Liskov (1999), resuelve el problema del consenso en sistemas distribuidos asíncronos en presencia de **fallas bizantinas** (nodos que pueden fallar por detención, enviar mensajes falsificados, mensajes contradictorios a diferentes partes de la red, o coludirse de forma maliciosa).

### Arquitectura, Vistas y Modelo de Nodos
- **Tolerancia a fallos:** Para tolerar hasta $f$ nodos bizantinos, el sistema requiere un mínimo de $N \ge 3f + 1$ nodos en total.
- **Vistas:** La ejecución progresa a través de configuraciones llamadas **vistas** ($v = 0, 1, 2, \dots$). En cada vista hay un único nodo **primario** (líder), determinado como $p = v \pmod N$, mientras que los demás nodos $N - 1$ actúan como réplicas de respaldo (*backups*).
- Si el primario falla o actúa de manera bizantina, un protocolo de cambio de vista (*view change*) transfiere el liderazgo a la siguiente vista.

---

### El Núcleo del Protocolo: Las Fases de Tres Vías (Three-Phase Protocol)

El núcleo de PBFT procesa las peticiones de los clientes mediante una secuencia de fases de mensajería rigurosa para garantizar un orden total de operaciones:

```
Cliente      Primario (Líder)      Réplica Backup 1      Réplica Backup 2
   |                |                     |                     |
   |--- REQUEST --->|                     |                     |
   |                |--- PRE-PREPARE ---->|                     |
   |                |----------------------- PRE-PREPARE ------>|
   |                |<--- PREPARE ------->|<--- PREPARE ------->| (Broadcast cruzado)
   |                |<--- COMMIT -------->|<--- COMMIT -------->| (Broadcast cruzado)
   |                |-- (Ejecuta operacion en maquina de estados local) --|
   |<--- REPLY -----|                     |                     |
   |<---------------------- REPLY --------|                     |
   |<-------------------------------------------- REPLY --------|
   | (Cliente espera f+1 respuestas identicas con el mismo resultado)
```

#### 0. Fase REQUEST (Solicitud del cliente)
El cliente $c$ solicita la ejecución de una operación de estado $o$ enviando un mensaje al nodo primario:
$$\langle\text{REQUEST}, o, t, c\rangle_{\sigma_c}$$
donde $t$ es una marca de tiempo monotónica utilizada para garantizar la semántica de ejecución única (*exactly-once*) y evitar ataques de repetición (*replay attacks*), y $\sigma_c$ es la firma criptográfica del cliente.

#### 1. Fase PRE-PREPARE (Pre-preparación)
Al recibir la solicitud, el nodo primario le asigna un número de secuencia entero $n$ estrictamente creciente en la vista actual $v$. Posteriormente, difunde a todas las réplicas de respaldo un mensaje `PRE-PREPARE` junto con la solicitud original $m$:
$$\langle\langle\text{PRE-PREPARE}, v, n, d\rangle_{\sigma_p}, m\rangle$$
donde $d = D(m)$ es el resumen criptográfico (digest / hash SHA-256) del mensaje de la petición $m$, y $\sigma_p$ es la firma del primario.
*Propósito:* Demostrar a las réplicas que el primario propone asignar el número de secuencia $n$ a la petición $m$ en la vista $v$. Cada réplica de respaldo valida la firma del primario, que la vista sea la actual ($v$), que el digest coincida con $m$, que el número de secuencia $n$ se encuentre dentro de los límites de ventana válidos ($h < n < H$), y que no haya aceptado previamente otro `PRE-PREPARE` para la misma vista y secuencia con un digest distinto.

#### 2. Fase PREPARE (Preparación)
Si la réplica de respaldo $i$ acepta el mensaje `PRE-PREPARE`, genera y difunde a **todos** los demás nodos de la red (incluyendo al primario y al resto de backups) un mensaje `PREPARE`:
$$\langle\text{PREPARE}, v, n, d, i\rangle_{\sigma_i}$$
Cada réplica almacena en su registro los mensajes recibidos y verifica sus firmas. Una réplica $i$ alcanza el estado **prepared**($m, v, n, i$) si y solo si tiene en su registro local:
1. El mensaje de solicitud $m$.
2. El mensaje `PRE-PREPARE` para $m$ en la vista $v$ con secuencia $n$.
3. Al menos **$2f$ mensajes `PREPARE`** válidos provenientes de réplicas distintas que coinciden exactamente en vista $v$, número de secuencia $n$ y digest $d$.
Contando el pre-prepare inicial (o su propio prepare), el nodo ha verificado un quórum de **$2f + 1$ mensajes consistentes**.
*Propósito:* Asegurar que los nodos no defectuosos acuerdan un orden total no ambiguo para las peticiones dentro de la misma vista $v$.

#### 3. Fase COMMIT (Compromiso)
Una vez que una réplica $i$ ha alcanzado la condición *prepared*, difunde a toda la red un mensaje `COMMIT`:
$$\langle\text{COMMIT}, v, n, d, i\rangle_{\sigma_i}$$
Cada réplica recolecta los mensajes de commit. Una réplica alcanza el predicado **committed-local**($m, v, n, i$) cuando:
1. El nodo ya se encuentra en estado *prepared* para esa petición.
2. Ha recibido y validado al menos **$2f + 1$ mensajes `COMMIT`** de nodos distintos que coinciden en vista $v$, secuencia $n$ y digest $d$ (incluyendo su propio mensaje `COMMIT`).
*Propósito:* Garantizar que el orden acordado persistirá de forma definitiva a través de cambios de vista (*view change*). Si un nodo está en estado *committed*, significa que suficientes réplicas honestas están *prepared*, impidiendo que un primario posterior en otra vista altere el orden ya comprometido.

#### 4. Fase REPLY (Ejecución y Respuesta al Cliente)
Una vez que la réplica $i$ alcanza el estado *committed-local* y todas las peticiones con número de secuencia previo ($< n$) ya han sido ejecutadas en su máquina de estados local:
1. La réplica ejecuta la operación $o$ modificando su estado interno.
2. Envía la respuesta directamente al cliente:
$$\langle\text{REPLY}, v, t, c, i, r\rangle_{\sigma_i}$$
donde $r$ es el resultado de la ejecución de la operación.
3. El cliente $c$ espera recibir **$f + 1$ mensajes `REPLY` idénticos** provenientes de réplicas distintas con la misma marca de tiempo $t$ y resultado $r$. Como a lo sumo $f$ nodos pueden ser bizantinos, recibir $f + 1$ respuestas idénticas garantiza matemáticamente que al menos una réplica honesta ejecutó la operación y que el resultado $r$ es auténtico y definitivo.

---

### ¿Por qué se necesitan $2f + 1$ mensajes iguales antes de ejecutar? (Fundamento Matemático)

En una red de $N = 3f + 1$ nodos donde hasta $f$ nodos pueden fallar de forma arbitraria o maliciosa (bizantina), el requerimiento de reunir exactamente **$2f + 1$ mensajes idénticos** antes de comprometer y ejecutar una operación es indispensable por tres fundamentos de la teoría de sistemas distribuidos:

1. **Liveness (Disponibilidad ante nodos caídos o mudos):**
   Hasta $f$ nodos del sistema pueden haberse detenido, desconectado o sufrir particiones de red sin responder jamás. Por lo tanto, un nodo no puede esperar recibir respuestas de los $N = 3f + 1$ participantes, pues se bloquearía indefinidamente. La cantidad máxima de mensajes que un nodo puede garantizar recibir en un entorno asíncrono con $f$ fallas es:
   $$N - f = (3f + 1) - f = 2f + 1$$
   Por ende, el quórum de decisión no puede ser mayor a $2f + 1$.

2. **Safety (Protección contra réplicas bizantinas / mentirosas):**
   De los $2f + 1$ mensajes recibidos para formar un quórum, en el peor de los casos hasta $f$ de ellos podrían provenir de nodos bizantinos que mienten, envían información contradictoria o intentan forzar un orden ilegítimo.
   Al exigir que los $2f + 1$ mensajes coincidan exactamente en vista, secuencia y digest, se garantiza que al menos:
   $$(2f + 1) - f = f + 1$$
   de esos mensajes provienen de réplicas honestas y correctas. Dado que $f + 1 > f$, la mayoría dentro del quórum está respaldada por nodos legítimos.

3. **Intersección de Quórums (Quorum Intersection Principle):**
   Consideremos dos quórums cualesquiera $Q_1$ y $Q_2$ de tamaño $2f + 1$ elegidos dentro del conjunto total de $N = 3f + 1$ nodos. Por el principio del palomar (intersección de subconjuntos):
   $$|Q_1 \cap Q_2| = |Q_1| + |Q_2| - N = (2f + 1) + (2f + 1) - (3f + 1) = f + 1$$
   La intersección entre dos quórums cualesquiera contiene siempre al menos $f + 1$ nodos. Dado que en todo el sistema hay a lo sumo $f$ nodos bizantinos:
   $$\text{Nodos honestos en la intersección} \ge (f + 1) - f = 1$$
   **Siempre existe al menos un nodo honesto y correcto en la intersección de dos quórums.**
   Dado que un nodo honesto jamás emite ni firma dos mensajes contradictorios para el mismo turno $(v, n)$, es matemáticamente imposible que un grupo de nodos prepare o comprometa una petición $m_1$ con secuencia $n$ y otro grupo prepare una petición contradictoria $m_2$ con la misma secuencia $n$.

De este modo, reunir $2f + 1$ mensajes iguales es la cota estricta que asegura acuerdo unívoco, previene bifurcaciones (*forks*) y garantiza que el orden de las operaciones persistirá aun si ocurre un cambio de vista (*view change*).

---

## REFERENCIAS: ##
- Castro, M., & Liskov, B. (1999). Practical Byzantine Fault Tolerance. In *Proceedings of the Third USENIX Symposium on Operating Systems Design and Implementation (OSDI '99)* (pp. 173-186). New Orleans, USA. https://pmg.csail.mit.edu/papers/osdi99.pdf
- GeeksforGeeks. (18 de marzo de 2024). Consensus algorithms in distributed system. https://www.geeksforgeeks.org/operating-systems/consensus-algorithms-in-distributed-system/
- TechTarget. (s. f.). What is a consensus algorithm? https://www.techtarget.com/whatis/definition/consensus-algorithm
- Crypto.com. (s. f.). Tolerancia práctica a faltas bizantinas (PBFT). Glosario. https://crypto.com/es/glossary/practical-byzantine-fault-tolerance-pbft
- Trakx. (s. f.). Prueba de Trabajo (PoW). Glosario. https://trakx.io/es/glosario/pow/
