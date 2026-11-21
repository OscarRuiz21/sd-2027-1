# T02 · Dos algoritmos de consenso que no son Raft

En los sistemas distribuidos, el consenso busca que varios nodos puedan mantener un mismo estado y ponerse de acuerdo sobre el orden de las operaciones, incluso cuando algunos nodos fallan o existen problemas de comunicación.

Además de Raft y Paxos, existen otros protocolos que utilizan diferentes mecanismos para lograr este objetivo. En esta tarea se revisan **Zab** y **PBFT**, que trabajan bajo modelos de fallos distintos.

---

## Zab (ZooKeeper Atomic Broadcast)

### ¿Qué es?

**Zab (ZooKeeper Atomic Broadcast)** es un protocolo utilizado por **Apache ZooKeeper** para mantener sincronizadas las réplicas de un sistema distribuido. Su funcionamiento se basa en un esquema de **líder y seguidores**, donde las operaciones que modifican el estado pasan por un líder que establece su orden.

Una de sus características principales es que las lecturas pueden realizarse directamente desde las réplicas, mientras que las escrituras deben ser coordinadas para que todos los nodos apliquen los cambios en el mismo orden.

### ¿Cómo funciona?

Cuando un cliente realiza una operación de escritura, el líder genera una propuesta y la envía a los demás nodos. Los seguidores guardan la propuesta y responden con un **ACK**.

Cuando el líder obtiene confirmación de una mayoría de nodos, la operación se considera comprometida (*committed*) y se informa al resto para que también la apliquen.

Zab utiliza un identificador llamado **zxid**, que permite establecer el orden de las transacciones. Este identificador incluye el número de época del líder y un contador de transacciones.

De manera general, el protocolo puede dividirse en dos situaciones:

* **Difusión normal:** el líder recibe, ordena y distribuye las operaciones.
* **Recuperación:** cuando el líder falla, se elige uno nuevo y se sincronizan las réplicas antes de continuar.

### ¿Qué ocurre si falla el líder?

Cuando los nodos detectan que el líder dejó de responder, se inicia una nueva elección. El objetivo es conservar las operaciones que ya fueron confirmadas y descartar aquellas que no llegaron a completarse.

Después de elegir al nuevo líder, las réplicas se sincronizan con él. Los nodos atrasados reciben las operaciones que les faltan y, cuando es necesario, eliminan operaciones que no fueron confirmadas.

De esta forma, el sistema puede continuar sin perder el estado que ya había sido acordado por una mayoría.

---

## PBFT (Practical Byzantine Fault Tolerance)

### ¿Qué es?

**PBFT (Practical Byzantine Fault Tolerance)** fue propuesto por **Miguel Castro y Barbara Liskov en 1999**. Su objetivo es alcanzar consenso incluso cuando algunos nodos pueden presentar **fallos bizantinos**, es decir, comportarse de forma incorrecta o enviar información diferente al resto.

Esto lo diferencia de protocolos como Raft o Zab, que normalmente consideran fallos en los que un nodo simplemente deja de responder.

### Tolerancia a fallos

PBFT puede tolerar hasta **f nodos defectuosos** si el sistema cuenta con al menos:

**N ≥ 3f + 1**

Por ejemplo, para tolerar un nodo bizantino se necesitan al menos cuatro nodos.

La idea es que, aunque algunos participantes se comporten incorrectamente, todavía exista una cantidad suficiente de nodos honestos para llegar a un acuerdo.

### ¿Cómo funciona?

PBFT trabaja con un nodo **Primary** y varios **Backups**. Cuando un cliente envía una petición, el protocolo pasa principalmente por tres fases:

1. **Pre-Prepare:** el Primary recibe la petición, le asigna un orden y la comunica a los demás nodos.
2. **Prepare:** los nodos verifican la propuesta y envían mensajes al resto para confirmar que recibieron la misma operación.
3. **Commit:** los nodos intercambian nuevas confirmaciones. Cuando se alcanza el quórum necesario, la operación se ejecuta y se envía la respuesta al cliente.

El cliente considera válida la respuesta cuando recibe suficientes respuestas iguales de diferentes réplicas.

### ¿Qué ocurre si falla un nodo?

Si un Backup falla o envía información incorrecta, los demás nodos pueden continuar mientras todavía exista el número necesario de participantes honestos.

Si el **Primary** deja de responder correctamente, los Backups pueden iniciar un **View Change**. Este mecanismo permite cambiar de Primary y continuar desde una nueva vista sin perder las operaciones que ya habían sido acordadas.

---

## Comparación general

| Característica     | Zab                             | PBFT                                                                |
| ------------------ | ------------------------------- | ------------------------------------------------------------------- |
| Modelo de fallos   | Fallos por caída                | Fallos bizantinos                                                   |
| Organización       | Líder y seguidores              | Primary y Backups                                                   |
| Quórum             | Mayoría                         | 2f + 1                                                              |
| Nodos mínimos      | 2f + 1                          | 3f + 1                                                              |
| Principal objetivo | Mantener réplicas sincronizadas | Mantener consenso ante nodos potencialmente maliciosos              |
| Uso representativo | Apache ZooKeeper                | Sistemas distribuidos con mayores requisitos de tolerancia a fallos |

---

## Fuentes

1. Junqueira, F. P., Reed, B. C., & Serafini, M. (2011). *Zab: High-performance broadcast for primary-backup systems*. IEEE/IFIP International Conference on Depen
