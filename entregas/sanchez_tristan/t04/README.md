# Tarea 04: ¿Qué reloj usan las tecnologías que conoces?

**Alumno:** Sánchez Mayén Tristán Qesen  
**Asignatura:** Sistemas Distribuidos — FI UNAM  

---

## 1. Google Spanner (TrueTime API)

### 1.1. Qué es y para qué se usa
Google Spanner es una base de datos relacional globalmente distribuida, de escala masiva y con consistencia estricta (ACID / Serializabilidad Estricta o External Consistency). Se utiliza en la infraestructura crítica de Google (servicios financieros, Google Play, anuncios) donde se requiere escalabilidad horizontal de lectura/escritura en múltiples centros de datos del mundo sin perder coherencia relacional.

### 1.2. Qué tiempo usa para ordenar
Usa un **reloj físico sincrónico con intervalo de incertidumbre acotado** mediante la API **TrueTime**. 
TrueTime no representa el tiempo como un instante único $t$, sino como un intervalo $[t.earliest, t.latest]$ donde la hora real se encuentra con absoluta certeza dentro de un margen de error $\epsilon$ (típicamente $\le 1 \text{ ms}$). Hardware especializado en cada datacenter (relojes atómicos de rubidio y receptores GPS independientes) mantiene este margen acotado.

### 1.3. Qué orden garantiza y hasta dónde
Garantiza **Consistencia Externa (External Consistency / Strict Serializability)** a **nivel global en todo el sistema**. 
Si una transacción $T_2$ se inicia después de que una transacción $T_1$ hace commit (en tiempo real global), el timestamp asignado a $T_2$ será estrictamente mayor que el de $T_1$ ($s_{T2} > s_{T1}$), sin importar en qué parte del planeta se ejecuten ambas transacciones.

### 1.4. Qué pasa cuando los relojes no coinciden
TrueTime asume que los relojes físicos tienen deriva y desajuste, por lo que aplica la regla de **Commit Wait** (espera en commit):
- Al realizar el commit de una transacción $T$, se le asigna como timestamp $s = t.latest$.
- El sistema **se detiene deliberadamente y espera** un tiempo $2\epsilon$ antes de hacer visibles los datos.
- Esto garantiza que el tiempo real global haya sobrepasado $s$ antes de que cualquier otra transacción en cualquier nodo pueda leer los cambios, eliminando carreras por desincronización de reloj.

---

## 2. Apache Kafka

### 2.1. Qué es y para qué se usa
Apache Kafka es una plataforma distribuida de transmisión de eventos (event streaming) distribuida por registros de publicaciones/suscripciones (*pub/sub*). Se utiliza para arquitecturas orientadas a eventos, procesamiento en tiempo real de flujos de datos (*stream processing*), ingesta de métricas y desacoplamiento de microservicios.

### 2.2. Qué tiempo usa para ordenar
Usa **números de secuencia lógicos estritos (Offsets)** combinados de forma opcional con un **reloj físico de pared (Timestamp de 64 bits)**.
- **Offset:** Número entero secuencial ($0, 1, 2, \dots$) asignado a cada mensaje por el nodo *Broker* líder.
- **Timestamp:** Puede ser `CreateTime` (asignado por el cliente que produce el mensaje usando su reloj local) o `LogAppendTime` (asignado por el Broker al escribir en el disco).

### 2.3. Qué orden garantiza y hasta dónde
Garantiza **orden total estricto únicamente dentro de una misma partición**.
- Dentro de la misma partición de un Topic, si el mensaje $A$ tiene offset $N$ y el mensaje $B$ tiene offset $N+1$, se garantiza que todos los consumidores procesarán $A$ antes que $B$.
- **No garantiza orden global entre diferentes particiones** de un mismo Topic ni entre distintos Topics.

### 2.4. Qué pasa cuando los relojes no coinciden
- Para el **ordenamiento y consistencia del log**, Kafka **ignora por completo el reloj físico** y confía exclusivamente en el **Offset monolítico** asignado secuencialmente por el líder de la partición.
- Si se usa `CreateTime` en los mensajes y el reloj del cliente está desincronizado (deriva de NTP), los timestamps guardados en las cabeceras pueden aparecer desordenados temporalmente, pero el **orden de entrega y consumo sigue rigurosamente el número de offset**.
- Si el reloj del cliente excede la tolerancia configurada (`message.timestamp.difference.max.ms`), el Broker rechaza el mensaje con un error `InvalidTimestampException`.

---

## 3. CockroachDB

### 3.1. Qué es y para qué se usa
CockroachDB es una base de datos SQL distribuida de código abierto diseñada para la nube (Cloud-Native Distributed SQL), inspirada en Google Spanner. Ofrece escalabilidad horizontal, alta disponibilidad transaccional (tolerancia a fallos de zona o región) y compatibilidad con el protocolo de PostgreSQL.

### 3.2. Qué tiempo usa para ordenar
Usa **Relojes Lógicos Híbridos (Hybrid Logical Clocks - HLC)**.
Un HLC combina el tiempo físico de pared (obtenido mediante NTP en milisegundos) con un contador lógico de Lamport ($l, c$). Cada nodo mantiene un timestamp $(physical\_time, logical\_counter)$ que avanza tanto por paso de tiempo físico como por intercambio de mensajes en la red.

### 3.3. Qué orden garantiza y hasta dónde
Garantiza **Consistencia de Lectura/Escritura Serializable (Serializable Isolation)** en **todo el sistema distribuido (global)** y orden causal para transacciones dependientes.

### 3.4. Qué pasa cuando los relojes no coinciden
- **Sin hardware dedicado:** A diferencia de Spanner, CockroachDB no requiere GPS ni relojes atómicos; utiliza servidores estándar sincronizados con NTP.
- **Ventana de incertidumbre y reinicio de lecturas:** Si la diferencia de tiempo entre el reloj del nodo local y el timestamp del dato que lee supera la incertidumbre máxima configurada ($\text{max\_offset}$, por defecto 500 ms), CockroachDB realiza un **Read Restart**: la transacción vuelve a ejecutarse en un timestamp más alto para evitar lecturas de datos en el "futuro".
- **Clock Skew Panic:** Si NTP falla y la deriva de reloj entre dos nodos excede el límite permisible ($\text{max\_offset}$), el nodo detecta el descuadre y **se apaga automáticamente (self-terminates)** para evitar la corrupción de datos o violaciones de serializabilidad.

---

## 4. Opcional: Reloj de Lamport en Java

### 4.1. Explicación de la causalidad
En la simulación realizada en la carpeta `lamport/`, se demuestra la relación de orden parcial causal ($a \to b \implies L(a) < L(b)$):
1. **Eventos locales / Envíos:** Cada servidor incrementa su reloj local de Lamport en $+1$ antes de registrar un evento o transmitir un mensaje.
2. **Recepción de mensajes:** Al recibir un mensaje con el timestamp remoto $L_{msg}$, el servidor actualiza su reloj local mediante $L_{local} = \max(L_{local}, L_{msg}) + 1$.
3. **Garantía:** Esto asegura que el timestamp de recepción de un mensaje sea siempre estrictamente mayor al timestamp en el que fue enviado, respetando la causalidad del sistema sin depender de un reloj físico.

Para poder usar esta simulacion se usan los siguientes comandos:
'''
bash
cd t04
javac lamport/LamportSimulation.java
java lamport.LamportSimulation > salida.txt
'''
---

## 5. Fuentes consultadas

1. Corbett, J. C., et al. (2013). *Spanner: Google’s Globally Distributed Database*. ACM Transactions on Computer Systems (TOCS), 31(3), 8.
2. Kleppmann, M. (2017). *Designing Data-Intensive Applications*. O'Reilly Media. (Capítulo 8: "The Trouble with Distributed Systems" y Capítulo 9: "Consistency and Consensus").
3. Kulkarni, S., et al. (2014). *Logical Physical Clocks and Consistent Snapshots in Globally Distributed Databases*. Computer Science Technical Report, SUNY Buffalo.
4. Apache Kafka Documentation. *Design & Implementation Details: Message Order and Semantics*. https://kafka.apache.org/documentation/#design
5. CockroachDB Docs. *Living Without Atomic Clocks (HLC Architecture)*. https://www.cockroachlabs.com/blog/living-without-atomic-clocks/
