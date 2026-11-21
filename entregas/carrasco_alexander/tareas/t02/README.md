# T02 · Dos algoritmos de consenso que no son Raft

**Alumno:** Diego Alexander Carrasco Quiñones
**Repositorio:** `sd-2027-1`
**Carpeta:** `entregas/carrasco_alexander/tareas/t02`

## Introducción

En los sistemas distribuidos es necesario que varios nodos puedan ponerse de acuerdo sobre las mismas operaciones y mantener un estado consistente. En clase vimos **Raft**, que utiliza un líder, elecciones y una mayoría para conseguir este acuerdo. Sin embargo, existen otros algoritmos que utilizan mecanismos diferentes y que además están diseñados para distintos tipos de fallos.

Para esta tarea investigué **Zab (ZooKeeper Atomic Broadcast)** y **PBFT (Practical Byzantine Fault Tolerance)**. Elegí estos dos porque permiten ver dos formas diferentes de resolver el problema del consenso: Zab está relacionado con la coordinación y sincronización de los servidores de ZooKeeper, mientras que PBFT está diseñado para soportar incluso fallos bizantinos.

---

## 1. Zab (ZooKeeper Atomic Broadcast)

**Zab** es el protocolo utilizado por **Apache ZooKeeper** para mantener sincronizados sus servidores. Su objetivo principal es que las operaciones realizadas sobre el sistema sean recibidas y aplicadas por los servidores en un orden consistente.

Su funcionamiento se puede entender de una manera sencilla. Dentro del grupo de servidores existe un **líder**, que recibe las operaciones y las convierte en propuestas. Después, el líder envía esas propuestas a los demás servidores, llamados seguidores. Los seguidores registran la propuesta y responden al líder. Cuando se obtiene un **quórum**, es decir, una cantidad suficiente de servidores que acepta la propuesta, esta puede considerarse comprometida y los servidores pueden aplicarla.

Una característica importante de Zab es que mantiene un **orden total de las operaciones**. Para identificar y ordenar las propuestas utiliza un identificador llamado **zxid** (ZooKeeper Transaction ID), que permite saber qué operación ocurrió antes y cuál después. Esto es importante porque los servidores deben terminar con el mismo estado y no aplicar las operaciones en un orden diferente.

Si el líder falla, el sistema necesita elegir otro y sincronizar nuevamente a los servidores antes de continuar con nuevas operaciones. De esta manera, el nuevo líder puede continuar el trabajo manteniendo el estado consistente entre las réplicas.

En pocas palabras, **Zab utiliza un líder, propuestas y un quórum para conseguir que los servidores de ZooKeeper mantengan el mismo orden de operaciones y un estado consistente**.

---

## 2. PBFT (Practical Byzantine Fault Tolerance)

**PBFT** es un algoritmo de consenso diseñado para un problema más complicado: los **fallos bizantinos**. Fue presentado por Miguel Castro y Barbara Liskov en 1999.

Un fallo común puede ocurrir cuando un servidor simplemente deja de funcionar. En cambio, un fallo bizantino significa que un nodo puede comportarse de manera incorrecta o arbitraria. Por ejemplo, podría enviar un mensaje a un servidor y un mensaje diferente a otro. Por esta razón, no basta con asumir que un nodo simplemente se apagará.

PBFT utiliza varias réplicas de un mismo servicio. Una de ellas funciona como **principal (primary)** y las demás como réplicas de respaldo. Cuando un cliente realiza una solicitud, el principal la propone al resto de las réplicas. Después, las réplicas intercambian mensajes para comprobar que están de acuerdo sobre la operación y su orden.

El protocolo utiliza tres etapas principales llamadas **pre-prepare, prepare y commit**. No es necesario pensar en ellas solamente como nombres: la idea es que las réplicas van intercambiando información y acumulando suficientes confirmaciones para asegurarse de que las réplicas correctas están de acuerdo antes de ejecutar la operación.

Una característica importante de PBFT es que, en su modelo clásico, para tolerar `n` réplicas con fallos bizantinos se necesitan al menos:

```text
3n + 1 réplicas
```

Por ejemplo, para tolerar un fallo bizantino se necesitan al menos cuatro réplicas. Esto permite que el sistema siga llegando a un acuerdo aunque una de ellas se comporte incorrectamente.

Si la réplica principal deja de funcionar correctamente, PBFT también contempla un **cambio de vista**, mediante el cual las réplicas pueden elegir otra como principal y continuar trabajando.

En pocas palabras, **PBFT busca que las réplicas correctas lleguen al mismo resultado incluso cuando algunas réplicas pueden comportarse de manera arbitraria**.

---

## 3. Diferencia entre Zab y PBFT

Aunque los dos son protocolos relacionados con el consenso y la replicación, están pensados para situaciones diferentes:

|                               | Zab                                       | PBFT                                                   |
| ----------------------------- | ----------------------------------------- | ------------------------------------------------------ |
| **Uso principal**             | Sincronización de servidores de ZooKeeper | Replicación tolerante a fallos bizantinos              |
| **Organización**              | Líder y seguidores                        | Principal y réplicas de respaldo                       |
| **Acuerdo**                   | Propuestas y quórum                       | Intercambio de mensajes entre réplicas                 |
| **Tipo de fallos**            | Fallos de servidores dentro de su modelo  | Fallos bizantinos                                      |
| **Característica importante** | Orden total de las operaciones            | Tolerancia a réplicas que se comportan incorrectamente |

La diferencia que considero más importante es el **tipo de fallo que intentan soportar**. Zab busca mantener sincronizadas las réplicas de ZooKeeper mediante un líder y un quórum, mientras que PBFT tiene que ser capaz de llegar a un acuerdo incluso cuando algunas réplicas pueden comportarse de manera arbitraria.

## Conclusión

Después de investigar estos dos algoritmos, pude entender que no existe una única forma de resolver el problema del consenso en sistemas distribuidos. Zab y PBFT tienen objetivos relacionados, pero utilizan mecanismos diferentes porque están pensados para distintos escenarios.

Zab me ayudó a entender cómo un grupo de servidores puede mantener el mismo orden de operaciones mediante un líder y un quórum. PBFT, por otro lado, muestra qué tan diferente es el problema cuando algunos nodos pueden comportarse de manera incorrecta y no solamente dejar de funcionar.

En comparación con Raft, estos algoritmos permiten ver que el consenso depende mucho del tipo de sistema y de los fallos que se quieran tolerar. Por eso, conocer diferentes protocolos ayuda a entender por qué un algoritmo puede ser adecuado para un escenario y otro algoritmo para una situación diferente.

## Fuentes

* Apache ZooKeeper. (s. f.). *ZooKeeper Internals*. Apache Software Foundation. https://zookeeper.apache.org/doc/current/zookeeperInternals
* Castro, M., & Liskov, B. (1999). *Practical Byzantine Fault Tolerance*. Proceedings of the Third Symposium on Operating Systems Design and Implementation (OSDI '99), 173–186. USENIX. https://www.usenix.org/legacy/publications/library/proceedings/osdi99/full_papers/castro/castro_html/castro.html 