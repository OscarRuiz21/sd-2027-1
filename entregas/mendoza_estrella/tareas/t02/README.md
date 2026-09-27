# T02 — Algoritmos de consenso

Para esta tarea elegí Viewstamped Replication y Prueba de trabajo (Bitcoin), porque uno se parece bastante a Raft y el otro resuelve el problema de una forma completamente distinta.

---

## 1. Viewstamped Replication (VR)

Viewstamped Replication es un algoritmo de consenso propuesto por Oki y Liskov en 1988, antes de que se publicara Paxos, aunque casi no se le conoce porque Paxos se hizo más famoso primero. Su funcionamiento se basa en un esquema de replicación primario-respaldo: existe un nodo primario que recibe las solicitudes de los clientes, les asigna un orden y las replica hacia los demás nodos, llamados respaldos, mientras estos permanecen en un rol pasivo esperando instrucciones del primario [1].

El tiempo en VR se organiza en vistas, que es el mismo concepto que los términos en Raft. Durante una vista, el primario asigna a cada operación un número de secuencia y la envía a los respaldos; la operación se considera confirmada únicamente cuando una mayoría de los nodos la ha guardado. Este mecanismo de mayoría es lo que garantiza que, aunque el sistema se divida temporalmente, sea imposible que dos grupos distintos confirmen operaciones contradictorias al mismo tiempo, ya que dos mayorías siempre comparten al menos un nodo en común.

Cuando el nodo primario deja de responder, los respaldos inician un proceso llamado cambio de vista, que es equivalente a una elección de líder en Raft. Este proceso garantiza que el nuevo primario incorpore todas las operaciones ya confirmadas, porque cualquier mayoría posterior necesariamente va a contener al menos un nodo que participó en la confirmación original. Una diferencia importante respecto a Paxos es que VR no necesita escribir a disco durante el flujo normal del protocolo, lo que en su momento le daba una ventaja en cuanto a rendimiento.

---

## 2. Prueba de trabajo (Bitcoin)

A diferencia de VR y Raft, la red de Bitcoin no tiene un nodo líder fijo ni un grupo cerrado de participantes; cualquier nodo puede unirse o salir de la red sin pedir autorización. Este entorno abierto necesita un mecanismo de consenso distinto, conocido como prueba de trabajo, propuesto originalmente por Satoshi Nakamoto en el documento fundacional de Bitcoin [2].

El mecanismo consiste en que cualquier nodo que quiera agregar un bloque nuevo a la cadena debe resolver un problema matemático difícil de calcular, pero fácil de verificar una vez que ya se resolvió. A este proceso se le llama minería, y el gasto real de energía y cómputo invertido es la evidencia de que el trabajo se hizo de verdad [3]. Cuando dos nodos resuelven este problema casi al mismo tiempo, la cadena se divide temporalmente; este conflicto se resuelve con la regla de la cadena más larga, según la cual los nodos honestos siguen extendiendo la cadena que acumula más trabajo computacional demostrado, no necesariamente la que tiene más bloques [4].

Este esquema logra consenso sin necesidad de una votación explícita, ya que modificar el historial de transacciones requeriría que un atacante controlara más de la mitad del poder de cómputo total dedicado a la red, lo cual resulta muy caro en la práctica [2]. Por esta misma razón, la práctica común es no considerar una transacción como definitiva hasta que el bloque que la contiene esté suficientemente enterrado bajo bloques posteriores, reduciendo así la probabilidad de que esa parte del historial sea revertida [4].

---

## Comparación general

Ambos algoritmos, junto con Raft, buscan que un conjunto de nodos llegue a un acuerdo sobre una secuencia única de eventos. La diferencia principal está en que Raft y VR necesitan conocer de antemano el número e identidad de los nodos participantes, mientras que la prueba de trabajo funciona en un entorno abierto donde los participantes cambian constantemente. Además, mientras Raft y VR llegan a consenso mediante mecanismos de votación y mayoría, la prueba de trabajo lo logra mediante el gasto verificable de un recurso físico.

---

## Referencias

[1] Wikipedia, "Replicación (informática)," *Wikipedia, la enciclopedia libre*. Disponible en: https://es.wikipedia.org/wiki/Replicaci%C3%B3n_(inform%C3%A1tica)

[2] S. Nakamoto, "Bitcoin: un sistema de efectivo electrónico entre pares," 2008. Disponible en: https://bitcoin.org/bitcoin.pdf

[3] Ledger Academy, "Qué es la prueba de trabajo," *Ledger*, 2022. Disponible en: https://www.ledger.com/es/academy/que-es-la-prueba-de-trabajo

[4] Wikipedia, "Prueba de trabajo (algoritmo de consenso distribuido)," *Wikipedia, la enciclopedia libre*. Disponible en: https://es.wikipedia.org/wiki/Prueba_de_trabajo_(algoritmo_de_consenso_distribuido)