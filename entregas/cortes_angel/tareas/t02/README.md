# Tarea 02

**Cortés Bolaños Angel David - 423066940** 
**Sistemas Distribuidos**



# Proof of Work (PoW)
Este algoritmo es el consenso original de Bitcoin no fue hecho justo para el bitcoin pero se hizo popular al ser usado por esta criptomoneda y se basa en que el derecho a actualizar el registro de transacciones se gana demostrando que se ha gastado una enorme cantidad de energía y poder computacional o sea es una competencia de fuerza bruta.
En primera las computadoras de la red, son llamadas mineros, estas agrupan las transacciones pendientes en un "bloque".
Para que la red acepte ese bloque, el minero debe pasarlo por una función criptográfica para encontrar un código único (hash) que empiece con una cantidad específica de ceros. No hay fórmulas para calcularlo; la única forma es haciendo combinaciones millones o billones de veces.
El primer minero que encuentra el código correcto lo transmite a la red. El código en sí es la prueba matemática de que el minero invirtió mucho tiempo, mucha energía y mucho hardware para encontrarlo.
Los demás nodos van a verificar el código en un instante. Si es correcto, el bloque se añade a la cadena y el minero recibe criptomonedas nuevas como pago por su esfuerzo.
Este mecanismo es muy seguro debido a que si alguien intenta hacer un movimiento malicioso necesitaría demasiada energía y hardware para lograrlo, es por eso que se considera casi imposible de lograr.


# Proof of Stake (PoS) 
Ahora este algoritmo elimina por completo la competencia de hardware y el gasto eléctrico. Aquí, el derecho a actualizar el registro se va a ganar demostrando compromiso económico con la red y esto se logra poniendo fondos bloqueados como garantía.
Los usuarios, ahora en este caso serán llamados validadores (ya no mineros), bloquean una cantidad de sus propias criptomonedas en un contrato de la red. Esto es a lo que se le conoce como hacer staking.
En lugar de poner a todos a competir matemáticamente al mismo tiempo, un algoritmo elige al azar a un validador para que proponga el siguiente bloque. Las probabilidades de ser elegido son proporcionales a la cantidad de dinero que tenga bloqueado entre más cantidad pongas de garantía maypr probabilidad serás de ser elegido.
Una vez que el validador elegido propone el bloque de transacciones, un grupo de otros validadores seleccionados al azar revisa que no haya transacciones falsas.
Si el bloque es válido, el validador recibe una recompensa. Pero si la red detecta que el validador intentó aprobar transacciones fraudulentas, el sistema le confisca y destruye automáticamente el dinero que había dejado como garantía.

## Fuentes
* Bitso. (2026, 13 de julio). *Proof of Work (PoW): qué es y cómo funciona*. https://bitso.com/mx/glossary/proof-of-work/
* Bitso. (2026, 13 de julio). *Proof of Stake (PoS): qué es y cómo funciona*. https://bitso.com/mx/glossary/proof-of-stake/