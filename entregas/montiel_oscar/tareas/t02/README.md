# T02 - Dos algoritmos de consenso que no son Raft ni Paxos

## Introduccion

Un algoritmo de consenso permite que varios nodos acuerden un mismo resultado aun cuando los mensajes se retrasen, algunos nodos fallen y, en ciertos modelos, algunos participantes actuen de forma maliciosa. En esta tarea se resumen dos alternativas a Raft y Paxos que no fueron parte de los ejemplos sugeridos en la consigna: **Avalanche (familia Snow)** y **Algorand BA\***.

---

## 1. Avalanche (familia Snow)

### Idea principal

Avalanche no basa el acuerdo en un lider permanente ni en que todos los validadores hablen con todos en cada ronda. Cada nodo mantiene una preferencia entre opciones que entran en conflicto, por ejemplo, dos transacciones que intentan gastar el mismo saldo. En vez de consultar a toda la red, pregunta repetidamente a una muestra aleatoria pequeña de validadores.

La familia se explica como una progresion: **Slush** introduce la votacion por muestras; **Snowflake** exige resultados consecutivos; **Snowball** conserva una medida de confianza acumulada; y Avalanche generaliza esta idea para conjuntos de transacciones que pueden depender unas de otras.

### Como funciona

1. Un nodo recibe propuestas que pueden ser incompatibles y elige inicialmente una preferencia.
2. Consulta a `k` validadores elegidos al azar acerca de su preferencia.
3. Si al menos un umbral `alpha` de esa muestra favorece una opcion, el nodo adopta o conserva esa preferencia.
4. En Snowball, cada respuesta suficientemente mayoritaria tambien aumenta el contador de confianza de la opcion favorecida.
5. Cuando la misma preferencia gana `beta` rondas consecutivas, el nodo la acepta como finalizada.

El mecanismo genera una propiedad llamada **metastabilidad**: una pequena ventaja inicial puede reforzarse ronda tras ronda, como una bola de nieve, hasta que los nodos correctos convergen en la misma opcion. La finalizacion es **probabilistica y ajustable**: aumentar los parametros de muestra y de rondas reduce la probabilidad de aceptar opciones contradictorias, aunque puede incrementar trabajo o latencia.

### Que lo distingue de Raft

Raft centraliza temporalmente la toma de decisiones en un lider elegido y utiliza una mayoria para confirmar entradas de un log lineal. La familia Snow no tiene lider fijo: cada validador sondea muestras aleatorias y decide localmente al observar suficiente apoyo repetido. Por ello no depende de rondas de eleccion de lider, pero su garantia de seguridad se expresa como una probabilidad configurable, no como una prueba determinista una vez alcanzado un quorum.

### Nota sobre su uso

La propuesta original Avalanche describia consenso sobre un DAG de transacciones. La documentacion actual de Avalanche indica que su red principal usa **Snowman**, una extension de Snowball que decide una cadena lineal de bloques; el motor DAG historico ya no se usa en la red principal. Esta diferencia no cambia la idea central del muestreo aleatorio repetido.

---

## 2. Algorand BA* (Byzantine Agreement)

### Idea principal

Algorand BA* busca acordar el siguiente bloque de transacciones sin requerir que todos los poseedores de la moneda participen en cada ronda. Su mecanismo central es el **sorteo criptografico**: cada cuenta ejecuta localmente una funcion aleatoria verificable (VRF) con su clave privada y datos publicos de la ronda. El resultado determina, de manera ponderada por su participacion, si fue elegida como proponente o miembro de un comite.

La VRF produce tambien una prueba que acompana el mensaje. Los demas nodos pueden comprobar que quien voto fue elegido correctamente, sin haber sabido de antemano quien integraria el comite. Asi se limita la posibilidad de atacar a miembros especificos antes de que hablen.

### Como funciona

1. En una ronda, el sorteo criptografico elige posibles proponentes. Cada uno puede proponer un bloque de transacciones.
2. Los usuarios seleccionados dan un voto inicial o *soft vote* para identificar un bloque candidato con prioridad alta.
3. Un comite nuevo, elegido de nuevo mediante VRF, vota para certificar el bloque. Si observa suficiente peso de voto, el bloque queda certificado.
4. Si no hay una certificacion clara, BA* continua con pasos de acuerdo binario: los comites efimeros votan entre aceptar el candidato o una opcion vacia hasta converger.
5. Todos los nodos, incluso los que no fueron seleccionados, verifican las pruebas y agregan el bloque acordado a su historial.

Los comites cambian en cada paso y sus miembros solo necesitan enviar su mensaje una vez. Esto reduce el costo de que todos participen y hace menos util que un adversario intente desconectar a un validador despues de identificarlo.

### Supuesto de seguridad

El articulo de Algorand plantea que el acuerdo se mantiene si una fraccion ponderada mayor a dos tercios del dinero pertenece a usuarios honestos. La ponderacion por participacion evita que un adversario gane influencia solo creando muchas identidades, que es el ataque Sybil. El protocolo separa seguridad y disponibilidad: una particion de red puede impedir que avance temporalmente, pero no debe hacer que dos bloques confirmados incompatibles sean aceptados bajo sus supuestos.

### Que lo distingue de Raft

Raft suele trabajar con un conjunto conocido y relativamente pequeno de replicas, mientras que BA* esta pensado para una red abierta en la que el comite se elige de forma aleatoria en cada etapa. En lugar de un lider fijo elegido por terminos, Algorand utiliza proponentes y votantes temporales seleccionados por VRF. Ademas, su modelo tolera comportamiento bizantino bajo el umbral de participacion honesta; Raft se disena principalmente para fallas de detencion o perdida de comunicacion, no para replicas bizantinas.

---

## Comparacion breve

| Aspecto | Avalanche / Snow | Algorand BA* |
|---|---|---|
| Forma de participar | Cada nodo consulta muestras aleatorias de validadores. | Comites efimeros seleccionados con VRF. |
| Lider permanente | No. | No; hay proponentes temporales por ronda. |
| Forma de decidir | Preferencia repetida y umbrales de confianza. | Votaciones de comite y acuerdo bizantino. |
| Tipo de garantia | Probabilistica, ajustable mediante parametros. | Seguridad bizantina bajo el supuesto de mas de dos tercios de participacion honesta. |
| Resistencia Sybil | Depende del conjunto y ponderacion de validadores del despliegue. | La influencia se pondera por participacion, no por cantidad de identidades. |

Los dos evitan el patron de un lider fijo de Raft, pero por rutas diferentes. Avalanche intenta que una preferencia se refuerce mediante consultas pequenas y frecuentes. Algorand limita quienes votan en cada paso mediante sorteo criptografico y usa acuerdo bizantino dentro de esos comites.

## Fuentes

1. Team Rocket. *Snowflake to Avalanche: A Novel Metastable Consensus Protocol Family for Cryptocurrencies*. 2019. arXiv. https://arxiv.org/pdf/1906.08936
2. Avalanche Builder Hub. *Consensus Protocols*. Consultado el 25 de septiembre de 2026. https://docs.avax.network/docs/nodes/architecture/consensus
3. Gilad, Y., Hemo, R., Micali, S., Vlachos, G. y Zeldovich, N. *Algorand: Scaling Byzantine Agreements for Cryptocurrencies*. SOSP 2017. https://people.csail.mit.edu/nickolai/papers/gilad-algorand.pdf
4. Chen, J. y Micali, S. *Algorand Agreement: Super Fast and Partition Resilient Byzantine Agreement*. Cryptology ePrint Archive, Report 2018/377. https://eprint.iacr.org/2018/377
