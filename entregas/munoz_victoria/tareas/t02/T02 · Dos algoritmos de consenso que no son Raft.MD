\# T02 · Dos algoritmos de consenso que no son Raft



\*\*Bitcoin: Proof of Work\*\*



Bitcoin utiliza la prueba de trabajo para decidir qué transacciones se agregan a su registro digital, llamado blockchain.



\* Las transacciones nuevas se reúnen en un grupo, como si se armara una página con varios movimientos.

\* Los mineros compiten usando computadoras para resolver un problema difícil. En pocas palabras, intentan encontrar una combinación de números que cumpla con las reglas de la red.

\* Resolver el problema requiere muchos intentos, electricidad y equipos especializados. Sin embargo, comprobar la respuesta correcta es rápido.

\* El minero que encuentra primero la solución comparte su bloque con los demás participantes de la red.

\* Los demás revisan que las transacciones sean válidas. Si todo está bien, agregan el bloque a la cadena.

\* Como recompensa, el minero recibe bitcoins y las comisiones de las transacciones incluidas.



Este sistema ayuda a proteger Bitcoin porque modificar una transacción antigua requeriría repetir una enorme cantidad de trabajo. Además, permite que miles de computadoras mantengan el mismo registro sin depender de un banco o una autoridad central.



\---



\*\*Ethereum: Proof of Stake\*\*



Ethereum utiliza la prueba de participación. En lugar de competir gastando mucha electricidad, los participantes dejan una cantidad de ETH (es la abreviatura de Ether, la criptomoneda propia de la red Ethereum) como garantía para ayudar a revisar y proteger la red.



\* Una persona deposita ETH para convertirse en validadora.

\* La red elige de manera aleatoria a algunos validadores para proponer nuevos bloques.

\* Otros validadores revisan esos bloques y confirman si las transacciones son correctas.

\* Si suficientes validadores están de acuerdo, el bloque se agrega a la cadena de Ethereum.

\* Los validadores que cumplen correctamente su trabajo reciben recompensas.

\* Si un validador intenta engañar a la red o actúa de forma incorrecta, puede perder parte de los ETH que dejó como garantía. A esta penalización se le llama slashing.



\---



\## Diferencia principal



| Aspecto | Bitcoin — Proof of Work | Ethereum — Proof of Stake |

| :--- | :--- | :--- |

| \*\*Quién participa\*\* | Mineros | Validadores |

| \*\*Recurso utilizado\*\* | Poder computacional y electricidad | ETH bloqueado como garantía |

| \*\*Cómo se elige quién propone el bloque\*\* | Competencia para resolver un problema criptográfico | Selección protocolaria de validadores |

| \*\*Incentivo de seguridad\*\* | Coste de la electricidad y del hardware | Riesgo de perder el ETH depositado |

| \*\*Recompensa\*\* | Subsidio de bloque y comisiones | Recompensas por proponer y validar |

| \*\*Impacto energético\*\* | Alto, debido a la minería | Menor, porque no requiere una competencia constante de cómputo |



En resumen, Bitcoin protege su red haciendo que los participantes gasten recursos en computadoras y electricidad. Ethereum la protege haciendo que los participantes arriesguen sus propios ETH: si actúan honestamente, pueden recibir recompensas; si intentan hacer trampa, pueden perder dinero.



\---



\## Fuentes usadas



\* Bitcoin.org. (s. f.). \*How does Bitcoin work?\* Recuperado el 20 de septiembre de 2026, de https://bitcoin.org/en/how-it-works

\* Bitcoin.org. (s. f.). \*Bitcoin: Un sistema de dinero en efectivo electrónico peer-to-peer\* \[Libro blanco de Bitcoin]. Recuperado el 20 de septiembre de 2026, de https://bitcoin.org/files/bitcoin-paper/bitcoin\_es.pdf

\* Bitcoin Developer Guide. (s. f.). \*Block chain\*. Recuperado el 20 de septiembre de 2026, de https://developer.bitcoin.org/devguide/block\_chain.html

\* Ethereum.org. (2026, 10 de septiembre). \*Proof-of-stake and security\*. https://ethereum.org/developers/docs/consensus-mechanisms/pos/

\* Ethereum.org. (2025, 17 de enero). \*Ethereum staking: How does it work?\* https://ethereum.org/staking/

\* Ethereum.org. (2026, 25 de agosto). \*Proof-of-stake vs proof-of-work\*. https://ethereum.org/developers/docs/consensus-mechanisms/pos/pos-vs-pow/

