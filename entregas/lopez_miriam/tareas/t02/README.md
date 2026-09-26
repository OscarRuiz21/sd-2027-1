# Dos algoritmos de consenso que no son Raft

## 1. Avalanche Consensus

Avalanche es un protocolo de consenso utilizado para que los nodos de una red distribuida puedan ponerse de acuerdo sobre qué transacciones deben ser aceptadas. Una de sus principales características es que no necesita que todos los nodos se comuniquen entre sí al mismo tiempo, sino que utiliza pequeñas consultas entre grupos de validadores.

Cuando un nodo necesita decidir si acepta una transacción, selecciona de forma aleatoria a un pequeño grupo de validadores y les pregunta cuál es su decisión. Si la mayoría del grupo coincide en una respuesta, el nodo comienza a tomar esa misma opción como su preferida. Este proceso se repite varias veces con diferentes grupos de validadores.

Por ejemplo, si un nodo pregunta a varios validadores si una transacción debe aceptarse y la mayoría responde que sí, aumenta su confianza en esa decisión. Después vuelve a preguntar a otro grupo y, si obtiene varias veces la misma respuesta, finalmente considera la transacción como aceptada.

Lo interesante es que cada nodo solamente consulta a una pequeña parte de la red en cada ronda. Conforme estas consultas se repiten, los nodos empiezan a coincidir cada vez más hasta llegar a una misma decisión. Esto ayuda a reducir la cantidad de mensajes que se necesitan enviar entre los participantes de la red.

Avalanche forma parte de una familia de protocolos relacionados con nombres como **Snowflake, Snowball y Snowman**. En particular, Snowman utiliza esta idea para organizar bloques en una cadena y es utilizado actualmente en las principales cadenas de la red Avalanche.

En pocas palabras, **Avalanche logra el consenso haciendo pequeñas consultas aleatorias entre los nodos varias veces hasta que la mayoría termina teniendo la misma decisión**.

---

## 2. Mysticeti

Mysticeti es un protocolo de consenso más reciente desarrollado para la red blockchain Sui. Su objetivo principal es reducir el tiempo que necesitan los validadores para ponerse de acuerdo y confirmar las transacciones.

Mysticeti utiliza una estructura llamada **DAG**, que significa grafo acíclico dirigido. A diferencia de una blockchain tradicional, donde normalmente pensamos que un bloque se coloca después de otro, en un DAG diferentes validadores pueden crear bloques de manera paralela y relacionarlos con bloques que ya fueron creados anteriormente.

En este protocolo, los validadores crean bloques que contienen transacciones y referencias hacia otros bloques anteriores. Con estas conexiones se va construyendo una estructura que permite conocer qué información ha sido observada y aceptada por otros participantes.

Después, Mysticeti analiza las relaciones entre estos bloques y los votos de los validadores para determinar qué bloques pueden ser aceptados y en qué orden deben procesarse. Una de sus diferencias respecto a otros protocolos basados en DAG es que busca evitar pasos adicionales de certificación, reduciendo la cantidad de comunicación necesaria entre los nodos.

Mysticeti comenzó a implementarse en la red principal de Sui durante **2024** y permitió reducir de manera importante los tiempos de consenso. De acuerdo con información publicada sobre la actualización, las transacciones que requieren consenso llegaron a tiempos cercanos a los 500 milisegundos.

En pocas palabras, **Mysticeti permite que varios validadores trabajen con bloques al mismo tiempo y después utiliza las conexiones y votos entre esos bloques para determinar el orden correcto de las transacciones**.

---

# Fuentes consultadas

## Avalanche Consensus

- Bit2Me News. (2025). *Avalanche cumple 5 años: así es cómo ha evolucionado esta red desde su lanzamiento*.  
  https://news.bit2me.com/avalanche-cumple-5-anos-asi-es-como-ha-evolucionado/

- Gate Learn. (2026). *¿Qué es el mecanismo de consenso de Avalanche? Un análisis en profundidad de Snowman y Avalanche Consensus*.  
  https://www.gate.com/es/learn/articles/what-is-avalanche-consensus

- Gate Learn. *Introducción a Avalanche - Mecanismo de consenso de Avalanche*.  
  https://miniapp.gate.com/es/learn/course/introduction-to-avalanche/avalanche-consensus-mechanism

## Mysticeti

- CriptoNoticias. (2024). *¿Por qué se ha disparado la criptomoneda SUI?*  
  https://www.criptonoticias.com/mercados/por-que-disparado-criptomoneda-sui/

- DiarioBitcoin. (2026). *La historia detrás de Mysticeti y la evolución del consenso en Sui*.  
  https://www.diariobitcoin.com/blockchain/la-historia-detras-de-mysticeti-y-la-evolucion-del-consenso-en-sui-alberto-sonnino-en-common-prefix-podcast/