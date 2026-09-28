# T02 - Dos algoritmos de consenso que no son Raft

## 1. ZAB (ZooKeeper Atomic Broadcast)

### ¿Qué es?

ZAB es un protocolo de consenso que utiliza ZooKeeper para mantener de
acuerdo a varios nodos dentro de un sistema distribuido. La idea es que
todos tengan la misma información y que los cambios se realicen en el
mismo orden, incluso si en algún momento uno de los nodos llega a
fallar. Para hacer esto se utiliza un líder que se encarga de coordinar
a los demás nodos, los cuales funcionan como seguidores.

### Elección del líder

Cuando el sistema comienza se tiene que elegir un líder entre los nodos
disponibles y también se hace una nueva elección si el líder que estaba
funcionando llega a fallar. Los nodos participan en esta elección y se
busca que el nuevo líder tenga la información más actualizada posible,
cuando la mayoría está de acuerdo se establece el nuevo líder y este se
encarga de sincronizar a los demás antes de continuar normalmente.

### ¿Cómo funciona?

Cuando llega una operación que va a cambiar la información, el líder
crea una propuesta y se la manda a los demás nodos. Los seguidores
reciben esta propuesta y responden con un ACK para indicar que la
recibieron, cuando el líder obtiene la respuesta de la mayoría confirma
el cambio y les avisa a los demás que ya pueden aplicarlo.

Con este proceso se busca que todos vayan realizando los cambios en el
mismo orden y así evitar que cada nodo termine con información
diferente. Si un seguidor falla puede volver a conectarse y actualizarse
con la información del líder, pero si el que falla es el líder se hace
una nueva elección y otro nodo toma su lugar para que el sistema pueda
seguir funcionando.

------------------------------------------------------------------------

## 2. PBFT (Practical Byzantine Fault Tolerance)

### ¿Qué es?

PBFT es un algoritmo de consenso que busca que varios nodos puedan
llegar a un acuerdo aunque alguno de ellos esté fallando o mande
información incorrecta. Esto es lo que se conoce como una falla
bizantina y lo que me pareció diferente es que aquí no solamente se toma
en cuenta que un nodo pueda dejar de funcionar, sino también que pueda
responder de una forma que no debería y aun así los demás tengan que
ponerse de acuerdo.

### Elección del líder

En PBFT también existe un nodo que funciona como líder, al cual se le
llama Primary, mientras que los demás funcionan como Backups. El Primary
recibe las solicitudes y comienza el proceso para que los demás nodos se
pongan de acuerdo, si llega a fallar o los demás detectan que no está
trabajando correctamente se puede hacer un cambio llamado View Change
para que otro nodo pase a ser el Primary y el proceso pueda continuar.

### ¿Cómo funciona?

Cuando un cliente manda una solicitud primero la recibe el Primary y
después este se encarga de compartirla con los demás nodos. A partir de
ahí comienzan a comunicarse entre ellos para revisar que estén
recibiendo la misma información y comprobar que suficientes nodos estén
de acuerdo antes de realizar la operación.

Este proceso tiene tres etapas principales llamadas **Pre-Prepare,
Prepare y Commit**. En la primera el Primary manda la propuesta, después
los nodos se comunican entre ellos para comprobar que recibieron lo
mismo y finalmente confirman que existe suficiente acuerdo para realizar
la operación. De esta manera no se depende solamente de lo que diga el
líder, ya que los demás nodos también comparan la información entre
ellos y pueden llegar a un consenso aunque alguno esté fallando.

------------------------------------------------------------------------

## Fuentes

-   *Zab Algorithm in Distributed Systems*. (2025, 23 julio).
    GeeksForGeeks.
    <https://www.geeksforgeeks.org/system-design/zab-algorithm-in-distributed-systems/>

-   Chand, M. (2024, 23 abril). *ZooKeeper Atomic Broadcast Protocol
    (ZAB): Understanding Its Mechanisms*. Medium.
    [https://medium.com/@mehar.chand.cloud/zookeeper-atomic-broadcast-protocol-zab-understanding-its-mechanisms-ffc157c08f5e](https://medium.com/@mehar.chand.cloud/zookeeper-atomic-broadcast-protocol-zab-understanding-its-mechanisms-ffc157c08f5e)

-   *Practical Byzantine Fault Tolerance(PBFT)*. (2025, 11 julio).
    GeeksForGeeks.
    <http://geeksforgeeks.org/computer-networks/practical-byzantine-fault-tolerancepbft/>
