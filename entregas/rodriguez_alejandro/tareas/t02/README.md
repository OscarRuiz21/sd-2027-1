# T02 - Dos algoritmos de consenso que no son Raft

**Sistemas Distribuidos (1959) - Grupo: 2**  
**Alumno:** Rodríguez Jaramillo Alejandro  
**UNAM / FI / DIE**  
**Semestre:** 2027-1  

---

## 1. Proof of Work (PoW)

Mecanismo de consenso original introducido por Bitcoin. Funciona como una competencia matemática o lotería donde los nodos, llamados **mineros**, gastan poder de cómputo y energía para tener el derecho de registrar nueva información. Funciona de la siguiente manera: 

1. Los usuarios envían transacciones a la red. Los mineros toman estas transacciones pendientes y las agrupan en un bloque temporal.
2. Todos los mineros compiten al mismo tiempo para resolver un acertijo criptográfico complejo: encontrar un **hash** específico. Esto requiere intentar millones de combinaciones por segundo, lo que consume mucha electricidad.
3. El primer minero que encuentra la respuesta correcta transmite su bloque terminado y la solución matemática al resto de la red.
4. Los demás nodos verifican que la solución sea correcta y que las transacciones sean válidas. Si todo está bien, aceptan el bloque, lo añaden a su base de datos y comienzan a competir por el siguiente bloque. 

En una red con este algoritmo implementado, siempre se acuerda que la versión oficial de los datos es la que tiene más trabajo matemático acumulado.

### Características principales de PoW:
* Consume cantidades masivas de energía eléctrica porque las computadoras operan al máximo intentando resolver acertijos.
* Para participar en PoW, se requiere comprar hardware especializado y costoso: equipos ASIC o granjas de GPUs.
* PoW protege la red haciendo que un ataque sea computacionalmente inviable.
* Es el sistema más probado a lo largo del tiempo, es resiliente y desvinculado de la riqueza inicial (no basta con tener dinero, hay que trabajar y gastar energía).

---

## 2. Proof of Stake (PoS)

Es utilizado actualmente por Ethereum. En lugar de competir quemando electricidad con computadoras potentes, los nodos, a los que se les llama **validadores**, utilizan un sistema de garantías económicas, que funciona de la siguiente manera: 

1. Para poder participar, un validador debe congelar una cantidad de sus propias criptomonedas en un contrato inteligente. Esto funciona como un depósito de seguridad llamado **Stake**.
2. Se selecciona al azar a un validador para que proponga el siguiente bloque de transacciones. Tener más monedas bloqueadas aumenta las probabilidades de ser elegido.
3. Una vez que el validador elegido propone el bloque, un grupo de otros validadores revisa que las transacciones sean correctas. 
4. Si el bloque es válido, se añade a la cadena y el proponente gana una recompensa. Si el proponente incluye datos falsos, el sistema aplica un castigo llamado **slashing**, destruyendo parte o todo su dinero depositado. El consenso se logra mediante el miedo a perder dinero real.

### Características principales de PoS:
* No consume mucha energía porque no requiere cálculos matemáticos intensivos.
* Para participar en PoS, basta con una simple computadora y una conexión a internet, pero se requiere capital para usar como garantía.
* PoS protege la red destruyendo el depósito de seguridad del atacante.
* Permite mayor escalabilidad, transacciones más rápidas, menor barrera de entrada técnica y protege al medio ambiente.

---

## Fuentes consultadas:

1. Coinbase, *Proof of Work (PoW) vs. Proof of Stake (PoS): ¿Cuál es la diferencia?*. Disponible en: [Coinbase Learn](https://www.coinbase.com/es-la/learn/crypto-basics/proof-of-work-pow-vs-proof-of-stake-pos-what-is-the-difference).
2. Hedera, *Proof-of-Stake vs Proof-of-Work*. Disponible en: [Hedera Learning](https://hedera.com/learning/proof-of-stake-vs-proof-of-work/).
3. Bitso, *Proof of Work*. Disponible en: [Bitso Glossary](https://bitso.com/mx/glossary/proof-of-work/).
4. Ethereum.org, *Prueba de participación (PoS)*. Disponible en: [Ethereum Documentation](https://ethereum.org/es/developers/docs/consensus-mechanisms/pos/).