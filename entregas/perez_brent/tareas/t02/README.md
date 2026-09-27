## T02 - Dos algoritmos de consenso que no son Raft
### 1. PBFT (Practical Byzantine Fault Tolerance)
A diferencia de Raft, que asume que los nodos pueden fallar apagándose o desconectándose, **PBFT** está diseñado para entornos donde los nodos pueden actuar de manera maliciosa, mentir o enviar información corrupta (*Fallos Bizantinos*).
#### ¿Cómo funciona?
PBFT utiliza un modelo donde hay un nodo líder y los demás son seguidores. Para que la red pueda tolerar $n$ nodos maliciosos o defectuosos, necesita un total de al menos $3n + 1$ nodos. EL proceso de concenso se divide en tres fases principales para cada petición que hace un cliente:

1. **Pre-prepare:** El nodo primario recibe la solicitud del cliente, le asigna un número de secuencia y transmite este mensaje a todos los nodos de respaldo.
2. **Prepare:** Los nodos de respaldo reciben el mensaje, verifican que sea válido y envían un mensaje de "preparación" a todos los demás nodos. Si un nodo recolecta suficientes confirmaciones de los demás, sabe que la mayoría de la red está de acuerdo con el mensaje.
3. **Commit:** Los nodos transmiten a toda la red que están listos para aplicar la operación. Cuando recolectan suficientes mensajes de *commit*, ejecutan la solicitud en su máquina local y le responden directamente al cliente.

Si el nodo primario empieza a actuar de forma maliciosa o deja de responder, los nodos de respaldo se dan cuenta y ejecutan un protocolo llamado "VIEW CHANGE" para destituirlo y elegir a un nuevo nodo primario.

### 2. Proof of Work (PoW - Prueba de Trabajo)
Este es el algoritmo que hizo famoso a Bitcoin. Mientras que Raft y PBFT están pensados para redes privadas o donde se conoce cuántos nodos hay, **Proof of Work** está diseñado para redes públicas y abiertas, donde cualquiera puede entrar o salir en cualquier momento y no hay confianza previa entre los participantes.
#### ¿Cómo funciona?
En PoW no hay elecciones directas para nombrar a un lider fijo; en su lugar, el *lider* se elige aleatoriamente en cada ronda mediante una competencia de fuerza bruta computacional.

1. **El acertijo:** Todos los nodos que quieren participar en el consenso agrupan las transacciones recientes e intentan resolver un acertijo criptográfico muy complejo y costoso en términos de energía y cálculo.
2. **La Propuesta:** El primer nodo que resuelve el acertijo obtiene el derecho de proponer el siguiente bloque de datos al resto de la red. Se convierte en el líder temporal de esa ronda.
3. **Verificación:** Los demás nodos reciben el bloque propuesto, verifican fácilmente que la solución matemática sea correcta y que las transacciones no sean fraudulenntas. Si todo está en orden, lo añaden a su registro y comienzan a trabajar en el siguiente bloque.
4. **Resolución de conflictos:** Si por casualidad la red se divide, la regla de oro de PoW es que la "cadena váida" es siempre la cadena más larga (la que tiene acumulada la mayor cantidad de trabajo computacional).

### Bibliografía
* **Binance Academy.** (s.f.). *Tolerancia a faltas bizantinas, explicada (PBFT)*. Recuperado de: https://academy.binance.com/es/articles/byzantine-fault-tolerance-explained 
* **Bitcoin.org.** (s.f.). *¿Cómo funciona Bitcoin? (Prueba de trabajo / PoW)*. Recuperado de: https://bitcoin.org/es/como-funciona 
* **GeeksforGeeks.** (2023). *Practical Byzantine Fault Tolerance (pBFT)*. Recuperado de: https://www.geeksforgeeks.org/practical-byzantine-fault-tolerance-pbft/
