# Dos algoritmos de consenso que no son Raft

## 1\. Proof of Work (Prueba de Trabajo - Bitcoin)

Funciona haciendo que los nodos (mineros) compitan por resolver un acertijo criptográfico complejo que requiere mucho poder computacional, el primero en resolverlo gana el derecho de añadir el siguiente bloque de transacciones a la cadena, los demás nodos validan rápidamente si la solución es correcta; al requerir esfuerzo físico y gasto de energía, se evita que nodos maliciosos tomen el control fácilmente, logrando consenso sin necesidad de un líder central.

## 2\. PBFT (Practical Byzantine Fault Tolerance)

PBFT tolera fallas bizantinas (nodos caídos o maliciosos) basándose en rondas de comunicación exhaustivas, un nodo actúa como líder (primario) y propone una acción, pasando por fases de pre-prepare, prepare y commit; para que una decisión sea aceptada, más de dos tercios (2/3) de los nodos deben estar de acuerdo, esto asegura que la red funcione correctamente siempre y cuando los nodos defectuosos sean menos de un tercio del total.

### Fuentes

* Castro, M., \& Liskov, B. (1999). Practical Byzantine Fault Tolerance.
* Nakamoto, S. (2008). Bitcoin: A Peer-to-Peer Electronic Cash System.

