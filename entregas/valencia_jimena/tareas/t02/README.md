## T02. Algoritomos de consenso. ##

- **Tendermint.** Es un motor de consenso que permite replicar una aplicación en múltiples máquinas, garantizando que las transacciones se registren de forma idéntica y en el mismo orden en todos los nodos de una red distribuida. Está compuesto por dos elementos principales: un motor de consenso (Tendermint Core) y una interfaz que conecta dicho motor con la capa de aplicación (Application Blockchain Interface). El primero se encarga de coordinar el consenso entre los nodos para mantener el mismo estado de la aplicación, mientras que la interfaz permite conectar el motor de consenso con aplicaciones desarrolladas en diferentes lenguajes de programación.

  **Rondas de consenso.** El proceso de validación en Tendermint se organiza en rondas secuenciales compuestas por tres pasos principales:

  1. Propuesta (Proposal). Un nodo validador designado en turno propone un nuevo bloque de transacciones.
  2. Prevoto (Prevote). Los nodos evalúan la propuesta y emiten un voto previo. Se requiere alcanzar un umbral de más de 2/3 del poder de voto total de la red para continuar.
  3. Precommit (Precommit). Los nodos emiten un segundo voto de confirmación. Nuevamente, para que el bloque sea aceptado definitivamente y agregado a la cadena, se necesita el respaldo de un umbral de más de 2/3 del poder de voto. Si no se alcanza este porcentaje, la ronda expira y se inicia una nueva ronda con otro proponente.


- **Proof of Authority (PoA).** Es un mecanismo de consenso en el que un grupo limitado de nodos autorizados se encarga de validar las transacciones y generar nuevos bloques. Los validadores son seleccionados previamente y deben cumplir con determinados criterios de identificación o confianza dentro de la red. Este mecanismo permite alcanzar el consenso de manera rápida y con un menor consumo de energía en comparación con mecanismos como Proof of Work.

  * **¿Cómo se eligen los validadores?** Los validadores son entidades previamente aprobadas y verificadas cuyas identidades reales están vinculadas directamente a sus nodos en la red.
  * **¿Cómo se turnan para producir bloques?** En lugar de competir mediante potencia computacional, los validadores autorizados se turnan de manera rotativa y ordenada para proponer y firmar nuevos bloques.
  * **¿Qué pasa si uno se porta mal?** Como las identidades de los validadores son públicas y verificadas, si alguno intenta actuar de forma maliciosa, es detectado fácilmente. La consecuencia es la expulsión inmediata de la red, la pérdida de su rol como validador y un severo perjuicio a su reputación y credibilidad profesional o institucional.

**Fuentes**

* Tendermint. (s.f.). Coinmetro: The Easiest Way to Buy Crypto with Confidence. https://coinmetro.com/es-es/glossary/tendermint
* What is Proof of Authority (PoA) and how does it work? | Crypto Academy. (s.f.). https://finst.com/en/learn/articles/what-is-proof-of-authority
* Proof of authority (POA) ¿sabés qué es? (s.f.). Despegá hacia el mundo cripto | Ripio Launchpad. https://launchpad.ripio.com/blog/proof-of-authority-poa-sabes-que-es
* What is Tendermint. (s.f.). Tendermint Core Documentation. https://docs.tendermint.com/v0.34/introduction/what-is-tendermint.html


