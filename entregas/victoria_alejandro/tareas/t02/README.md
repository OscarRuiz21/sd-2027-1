# Tendermint

Tendermint funciona como un comité donde un grupo de servidores o **validadores** necesita ponerse de acuerdo para guardar un nuevo bloque de información y se protege de que uno de los servidores sea malicioso o haya una falla en alguno de los validadores. 

Tendermint se basa en un sistema de votación estricto y por rondas que se componen de 4 pasos.

1. **Proponer/Propose:** El validador líder sugiere un nuevo bloque de datos para agregar al historial.
2. **Pre-votar/Pre-vote:** Todos los demás validadores reciben la propuesta, la revisan para asegurar que los datos sean válidos, y emiten un voto a favor o en contra. Para avanzar, se necesita que **más de 2/3** de la red apruebe el bloque.
3. **Pre-confirmar/Pre-commit:** Una vez que más de los 2/3 de validadores aprobaron el bloque en el paso anterior, emiten un segundo voto de confirmación. Para este paso también se requiere que **2/3** de los validadores confirmen.
4. **Confirmar/Commit:** El bloque se añade de forma permanente al historial. 

Con este algoritmo, una vez que se hace commit a un bloque, se guarda instantáneamente en todos los servidores activos. Si no se alcanzan los votos, la red hace una pausa, cambia de líder y lo intenta de nuevo en la siguiente ronda.

---

Para elegir a un líder Tendermint no usa números aleatorios o cálculos matemáticos. Utiliza un sistema determinista de **round-robin ponderados por el stake** de cada validador.

El stake está determinado por la cantidad de tokens que un validador tiene bloqueados o que le han sido delegados. A mayor cantidad de tokens, más veces será elegido como líder a lo largo del tiempo.

1. **Prioridad base:** Todos los validadores comienzan con una puntuación de prioridad en cero.
2. **Incremento de prioridad:** En cada ronda, la prioridad de cada validador aumenta en una cantidad igual a su stake. 
3. **Selección:** El validador que acumula la mayor puntuación de prioridad es elegido como el líder de esa ronda.
4. **Reinicio del líder:** Una vez que un validador es elegido líder y propone el bloque, se le resta de su prioridad el poder de voto total de toda la red, haciendo que su puntuación se vuelva un número negativo y así dar chance a que otro validador sea líder en la siguiente ronda.

Si el validador elegido está desconectado, su servidor se cae, o propone un bloque inválido, la red se da cuenta porque falla el pre-vote. Cuando eso pasa, el protocolo simplemente avanza a la **siguiente ronda** para intentar proponer ese mismo bloque, y el turno pasa automáticamente al siguiente validador con la prioridad más alta.

# Proof of Stake

Proof os Stake utiliza un sistema de depósitos para mantener la honestidad de cada miembro de la red. A diferencia de Proof of Work, no se necesita minar con montones de computadoras para generar ganancias.

Este algoritmo se conforma de 4 pasos:

1. **Depósito/Stake:** Los participantes o validadores bloquean cierta cantidad de sus propias criptomonedas en el sistema. Este es el depósito.
2. **Selección/Lotería:** Con la cantidad de criptomonedas que se dejan como depósito se le asigna a cada quién cierta cantidad de boletos de lotería. Luego que ya todos tienen sus boletos se selecciona uno al azár y su dueño es seleccionado para validar el siguiente bloque de transacciones.
3. **Trabajo:** El validador escogido hace una validación de todas las transacciones del bloque, las empaqueta y las presenta al resto de validadores.
4. **Recompensas y Castigos:** Si el validador es honesto y los demás miembros verifican que no hubo transacciones inválidas, se guardan las transacciones y el validador que hizo el trabajo recibe una propina. En caso que el validador sea deshonesto y la red lo rechaza, el sistema el quita todas las criptomonedas que tenía en su depósito.

Este algoritmo castiga fuertemente a los validadores que hacen trampa, por lo que el único camino posible es ser escogido y recibir propina para aumentar la cantidad de criptomonedas.

#Referencias
Academy, B. (2026). ¿Qué es Prueba de participación / Proof of Stake (PoS)? Bit2Me Academy. https://academy.bit2me.com/que-es-proof-of-stake-pos/ Community, E., & Community, E. (s.f.). Proof-of-stake (POS). ethereum.org. https://ethereum.org/developers/docs/consensus-mechanisms/pos/ Consensus | Tendermint Core. (s.f.). https://docs.tendermint.com/master/tendermint-core/consensus/ Tendermint Consensus. (2023). Medium. https://medium.com/@erdemegeeroglu/tendermint-consensus-5c3775fa90df What is Tendermint | Tendermint Core. (s.f.). https://docs.tendermint.com/master/introduction/what-is-tendermint.html