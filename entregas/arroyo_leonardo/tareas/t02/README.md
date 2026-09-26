# T02: Dos algoritmos de consenso que no son Raft

Antes de explicar los algoritmos utilizados por Bitcoin y Ethereum, es importante entender un problema básico de las redes blockchain: no existe una única computadora encargada de decidir qué transacciones son válidas. En una blockchain participan muchas computadoras, llamadas **nodos**, que mantienen copias de la información de la red. Por ello, necesitan algún mecanismo que permita ponerse de acuerdo sobre qué bloque debe agregarse a la cadena y cuál es el estado correcto de la blockchain. A este mecanismo se le conoce como **algoritmo de consenso**.

Un bloque es un conjunto de información que contiene varias transacciones realizadas por los usuarios. También incluye información que permite relacionarlo con el bloque anterior. Cuando un nuevo bloque es aceptado, se agrega a la blockchain. De esta manera se forma una cadena de bloques conectados entre sí.

Un **hash** es un valor obtenido al introducir información en una función matemática especial llamada función hash. Se puede ver algo así como una especie de huella digital de los datos. Si se modifica incluso una pequeña parte de la información original, el hash resultante cambia completamente.

El objetivo del hash es permitir comprobar fácilmente si cierta información ha sido modificada. En una blockchain también permite relacionar los bloques entre sí, ya que cada bloque contiene información relacionada con el hash del bloque anterior. En Bitcoin, además, el hash se utiliza como parte del mecanismo de Proof of Work.

Una **bifurcación o fork** ocurre cuando existen temporalmente dos versiones diferentes de la blockchain. Por ejemplo, dos participantes podrían crear un bloque válido casi al mismo tiempo, pongamos de ejemplo a estos bloques:

Bloque 100 → Bloque 101A
Bloque 100 → Bloque 101B

En ese momento existen dos posibles caminos. La red necesita una regla para decidir cuál de ellos debe considerarse como la cadena principal. Bitcoin y Ethereum utilizan mecanismos diferentes para resolver esta situación.

# Bitcoin: Proof of Work (PoW)

Bitcoin utiliza un algoritmo de consenso llamado **Proof of Work**, o prueba de trabajo. Su objetivo es permitir que los participantes de la red decidan quién puede agregar el siguiente bloque y cuál cadena debe considerarse válida.

Aquí aparece el concepto de minero. Un **minero** es un participante de la red Bitcoin que utiliza poder computacional para intentar crear un nuevo bloque. Primero reúne diferentes transacciones pendientes y construye un bloque candidato. Después comienza a realizar una gran cantidad de cálculos para intentar encontrar un hash que cumpla con las condiciones establecidas por la red. Los mineros realizan este trabajo porque existe una recompensa económica. El minero que logra producir un bloque válido puede recibir la recompensa correspondiente al bloque y las comisiones de las transacciones incluidas.

Bitcoin necesita una forma de hacer que crear un bloque sea difícil, pero comprobarlo sea sencillo. Para conseguirlo, Bitcoin establece un objetivo de dificultad. El hash generado por el bloque debe encontrarse por debajo de cierto valor establecido por la red. El minero calcula repetidamente el hash del encabezado del bloque intentando obtener un resultado válido. No existe una forma práctica de saber previamente qué intento producirá el resultado necesario. Esto obliga a los mineros a realizar una gran cantidad de cálculos. Sin embargo, una vez encontrado un resultado correcto, los demás nodos pueden comprobarlo rápidamente realizando el cálculo correspondiente.

El **nonce** es un número que el minero puede modificar para producir diferentes hashes. Cada cambio produce un hash diferente. El minero prueba valores hasta encontrar uno que permita obtener un hash que cumpla con el objetivo de dificultad. Por eso suele decirse que los mineros están "buscando un número". El número no tiene importancia por sí mismo. Su función es permitir modificar los datos utilizados para calcular el hash hasta encontrar un resultado válido. En la práctica, los mineros también pueden modificar otros campos cuando agotan el espacio disponible para el nonce.

Proof of Work hace costoso producir nuevos bloques o intentar modificar el historial de Bitcoin. Para alterar bloques anteriores, un atacante tendría que volver a realizar el trabajo computacional necesario y competir contra el poder de cómputo del resto de la red. Por ello, la seguridad de PoW depende en gran medida del trabajo computacional acumulado.

Cuando un minero encuentra una solución válida, transmite su bloque a la red. Los demás nodos verifican las transacciones y comprueban que el Proof of Work sea correcto. En ocasiones, dos mineros pueden encontrar bloques válidos casi simultáneamente. Esto produce una bifurcación temporal. Bitcoin resuelve estas situaciones siguiendo la cadena válida que representa la mayor cantidad de trabajo acumulado. Los mineros continúan trabajando y, eventualmente, una de las ramas acumula más trabajo. Los nodos terminan convergiendo hacia ella y la otra rama deja de formar parte de la cadena principal. Por este motivo, normalmente se esperan varias confirmaciones antes de considerar una transacción como difícil de revertir.

# Ethereum: Proof of Stake (PoS)

Ethereum utiliza actualmente **Proof of Stake**, o prueba de participación. A diferencia de Bitcoin, no necesita que los participantes compitan realizando enormes cantidades de cálculos. Utiliza validadores que depositan ETH como garantía para participar en el consenso.

Aquí, a diferencia de Bitcoin con sus "mineros", aquí aparecen los validadores. Un **validador** es un participante encargado de ayudar a verificar y proponer bloques en Ethereum. Para activar directamente un validador se depositan **32 ETH**. Estos ETH funcionan como una garantía económica de que el participante seguirá correctamente las reglas del protocolo.

Aparece un concepto llamado "staking". El **staking** consiste en bloquear o depositar criptomonedas para participar en el funcionamiento y seguridad de una red Proof of Stake. En Ethereum, los validadores ponen ETH en staking. La idea consiste en crear un incentivo económico: comportarse correctamente puede producir recompensas, mientras que determinados comportamientos maliciosos pueden provocar penalizaciones y, en casos graves, **slashing**, que implica la pérdida de parte del ETH puesto como garantía.

Ethereum divide el tiempo en intervalos llamados **slots**, que duran aproximadamente 12 segundos. Para cada slot se selecciona un validador encargado de proponer un bloque. Otros validadores participan enviando votos llamados **attestations**, con los que indican qué bloques consideran parte correcta de la cadena. Para realizar estas selecciones Ethereum necesita aleatoriedad, y aquí interviene RANDAO.

**RANDAO** es un mecanismo utilizado para contribuir a generar la aleatoriedad necesaria para seleccionar responsabilidades de los validadores. La idea importante es que la selección no dependa simplemente de que alguien decida manualmente quién será el siguiente validador. Los validadores realizan contribuciones criptográficas que se combinan para mantener una fuente de pseudoaleatoriedad utilizada por el protocolo. Ethereum complementa este mecanismo con información que hace que las selecciones futuras sean difíciles de predecir con demasiada anticipación. De esta manera, la red puede distribuir funciones entre los validadores sin depender de una autoridad central que decida quién participa en cada momento.

Proof of Stake busca proteger la blockchain mediante incentivos y penalizaciones económicas. Un participante que quiera atacar el sistema necesita controlar una cantidad importante de ETH en staking. Además, ciertos comportamientos maliciosos pueden provocar que pierda parte de los activos que utilizó como garantía. Por ello, Ethereum no necesita que los validadores gasten energía continuamente compitiendo para encontrar un hash como ocurre con los mineros de Bitcoin.

Ethereum utiliza un sistema de consenso conocido como **Gasper**, que combina principalmente dos componentes: LMD-GHOST y Casper FFG. **LMD-GHOST** ayuda a decidir qué rama debe seguir la red cuando existen diferentes bloques posibles. Para hacerlo utiliza las attestations de los validadores y considera el peso del ETH en staking asociado a esos votos. Por otra parte, **Casper FFG** proporciona el mecanismo de finalidad.

La blockchain utiliza puntos de control o *checkpoints*. Cuando se alcanza el apoyo necesario de los validadores, estos puntos pueden ser justificados y posteriormente finalizados. La finalidad requiere una supermayoría de aproximadamente dos tercios del ETH en staking participante en el consenso. Una vez que un bloque queda finalizado, revertirlo requeriría violar las condiciones de seguridad del protocolo y provocaría importantes penalizaciones económicas.

# Diferencia entre Bitcoin y Ethereum

La principal diferencia se encuentra en el recurso utilizado para proteger la red.

En **Bitcoin con Proof of Work**, los mineros utilizan equipos especializados, electricidad y poder computacional. La dificultad de atacar la red proviene del enorme trabajo computacional necesario para competir contra el resto de los mineros.

En **Ethereum con Proof of Stake**, los validadores depositan ETH como garantía. La seguridad depende de los incentivos económicos, las votaciones de los validadores y las penalizaciones que pueden aplicarse cuando determinados participantes rompen las reglas.

Por ello, Bitcoin utiliza el **trabajo computacional** como base de su mecanismo de consenso, mientras Ethereum utiliza principalmente **capital económico puesto en staking**.

Otra diferencia importante aparece al resolver bifurcaciones. Bitcoin sigue la cadena válida con mayor trabajo acumulado. Ethereum utiliza LMD-GHOST, que toma en cuenta las attestations de los validadores.

Finalmente, Bitcoin ofrece una seguridad de carácter probabilístico: cuantos más bloques se agregan después de una transacción, más difícil resulta reorganizar la cadena para revertirla.

Ethereum agrega además el concepto de **finalidad**. Una vez que determinados checkpoints quedan finalizados mediante el consenso de los validadores, revertirlos implicaría una violación grave de las reglas de consenso y fuertes consecuencias económicas para los validadores involucrados.
