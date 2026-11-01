# T04 · ¿Qué reloj usan las tecnologías que conoces?

## Apache Kafka

**Qué es y para qué se usa.** Kafka es una plataforma distribuida de streaming de eventos; los productores escriben eventos en _topics_, y los consumidores los leen de forma asíncrona. Se utiliza principalmente para pipelines de datos en tiempo real, agregación de logs, eventos analíticos y mensajería entre microservicios desacoplados.

**Qué tiempo usa para ordenar.** Kafka **no** ordena por el reloj de pared. Aunque cada registro puede incorporar un _timestamp_ (por ejemplo, el tiempo en que el productor creó el mensaje o el broker lo recibió), el orden real de almacenamiento y consumo lo define la posición del mensaje en el registro de la partición (el **offset**). Kafka utiliza un **número de secuencia monótono implícito por partición** asignado por el broker líder, no un reloj lógico tipo Lamport ni relojes vectoriales.

**Qué orden garantiza.** Kafka garantiza orden estricto **únicamente dentro de una misma partición**. Si varios mensajes comparten la misma llave (_message key_), el particionador los envía a la misma partición y el consumidor los procesará exactamente en el orden en que fueron agregados al log. No garantiza orden global entre distintas particiones de un mismo topic.

**Qué pasa si los relojes no coinciden.** Para el orden de entrega y almacenamiento, Kafka **lo ignora**: un mensaje con un timestamp de pared anterior a otro se leerá de acuerdo a su offset en la partición. Sin embargo, en capas superiores como Kafka Streams, el timestamp cobra relevancia para el cálculo de ventanas de agregación (_event time_) y el manejo de registros atrasados o fuera de orden.

---

## Amazon DynamoDB

**Qué es y para qué se usa.** DynamoDB es una base de datos NoSQL totalmente administrada por AWS que ofrece almacenamiento de tipo clave-valor y documentos. Está diseñada para ofrecer latencias de un solo dígito de milisegundos a cualquier escala, usándose habitualmente en carritos de compras, perfiles de usuario, sesiones y catálogos.

**Qué tiempo usa para ordenar.** DynamoDB **no** impone un reloj lógico o físico global propio para ordenar los datos. El orden de los registros lo determina la **Sort Key (clave de ordenamiento)** seleccionada por el diseñador de la tabla. Esta clave puede ser un timestamp, un número de versión incremental o un identificador ordenable. Se trata de un **orden lógico definido a nivel de aplicación**.

**Qué orden garantiza.** Garantiza orden **dentro de una partición lógica** (un grupo de elementos que comparten la misma _Partition Key_). Los elementos con igual _Partition Key_ se almacenan ordenados de forma ascendente o descendente según el valor de su _Sort Key_. No existe garantía de orden global entre elementos con diferentes claves de partición.

**Qué pasa si los relojes no coinciden.** DynamoDB almacena y compara los valores de la _Sort Key_ tal cual le son enviados: **no** sincroniza ni corrige desviaciones de reloj físico entre distintos clientes o servidores de aplicación. Si dos clientes escriben basándose en su reloj local desincronizado, el orden en la sort key puede reflejar timestamps erróneos. Por ello, la buena práctica en sistemas distribuidos sobre DynamoDB es delegar la generación de la sort key a un backend centralizado, usar contadores atómicos o emplear identificadores ordenables causalmente (como ULID o UUIDv7).

---

## Google Cloud Spanner

**Qué es y para qué se usa.** Spanner es el sistema de gestión de bases de datos relacionales globalmente distribuido de Google Cloud. Proporciona soporte para esquemas relacionales, consultas SQL, transacciones ACID distribuidas y alta disponibilidad con replicación síncrona a nivel mundial.

**Qué tiempo usa para ordenar.** Spanner usa un **enfoque híbrido físico-estadístico** basado en la API **TrueTime**. TrueTime no asume que los relojes físicos de las máquinas estén perfectamente sincronizados; en su lugar, sincroniza mediante una combinación de receptores GPS y relojes atómicos independientes en cada centro de datos. TrueTime expone el tiempo como un intervalo de incertidumbre `[earliest, latest]`, donde la hora real se encuentra con absoluta certeza dentro de un margen acotado (típicamente $\le 7\text{ ms}$).

**Qué orden garantiza.** Garantiza **consistencia externa** (_external consistency_ o _strict serializability_): si una transacción $T_2$ inicia después de que una transacción $T_1$ realiza su commit (en tiempo real absoluto), el timestamp de commit asignado a $T_2$ será estrictamente mayor que el de $T_1$. Esta garantía es **global** y abarca todo el sistema distribuido a través de cualquier número de particiones y continentes.

**Qué pasa si los relojes no coinciden.** Spanner gestiona directamente la incertidumbre del reloj físico mediante la técnica de **commit wait**: cuando una transacción calcula su commit timestamp $t = \text{latest}$, el servidor espera deliberadamente hasta que TrueTime garantice que el tiempo actual del mundo real es mayor que $t$ ($\text{earliest} > t$) antes de liberar los bloqueos y hacer visible el resultado. De este modo, la desincronización física de relojes nunca compromete la causalidad ni el orden serializable.

---

### Resumen Comparativo

| Tecnología | Mecanismo de tiempo / orden | Alcance de la garantía de orden | Comportamiento ante relojes desincronizados |
| :--- | :--- | :--- | :--- |
| **Apache Kafka** | Secuencia monótona / Offset por partición | Por partición (no global) | Ignora timestamps físicos para orden de entrega en log |
| **Amazon DynamoDB** | Sort Key elegida por el desarrollador (a nivel aplicación) | Por Partition Key (partición lógica) | No corrige desfases; delega la coherencia al diseño de claves del usuario |
| **Google Cloud Spanner** | Híbrido físico con intervalo de incertidumbre (TrueTime) | Global (consistencia externa / strict serializability) | Emplea *commit wait* para absorber el margen de error de reloj |

---

## Opcional: Reloj de Lamport en Java

En el directorio [`lamport/`](file:///home/brent/SDistribuidos/sd-2027-1/entregas/perez_brent/tareas/t04/lamport/) se encuentra la simulación en Java solicitada:

- Código fuente: [`LamportSimulator.java`](file:///home/brent/SDistribuidos/sd-2027-1/entregas/perez_brent/tareas/t04/lamport/LamportSimulator.java)
- Salida de ejecución: [`salida.txt`](file:///home/brent/SDistribuidos/sd-2027-1/entregas/perez_brent/tareas/t04/lamport/salida.txt)

### Diseño de la Simulación

La simulación modela **3 servidores concurrentes** (`Server-A`, `Server-B`, `Server-C`), ejecutándose en hilos independientes y comunicándose a través de canales con colas bloqueantes (`BlockingQueue<Message>`) que introducen latencias de red variables. Cada servidor envía 4 mensajes (dentro del rango requerido de 3 a 5) a pares aleatorios, alternando con eventos locales internos.

Cada servidor gestiona su reloj lógico mediante `AtomicLong clock` implementando las dos reglas fundamentales del algoritmo de Leslie Lamport (1978):

1. **Regla 1 (Eventos locales y de envío):**
   Antes de ejecutar un evento interno o despachar un mensaje, el proceso incrementa su reloj local:
   ```java
   long t = clock.incrementAndGet(); // L = L + 1
   ```
   En los envíos, el mensaje viaja portando dicho timestamp lógico $t_{send}$.

2. **Regla 2 (Eventos de recepción):**
   Al recibir un mensaje con timestamp $t_{msg}$, el servidor actualiza atómicamente su reloj con el máximo entre su reloj local y el recibido, sumando uno:
   ```java
   long t = clock.updateAndGet(current -> Math.max(current, msg.timestamp()) + 1);
   ```

### ¿Por qué la salida respeta la causalidad?

La condición de consistencia de reloj de Lamport establece que para dos eventos $a$ y $b$:
$$\text{Si } a \to b \text{ (a sucedió antes de b causally), entonces } L(a) < L(b)$$

La relación *happened-before* ($\to$) se compone de dos casos base y su clausura transitiva:

1. **Causalidad intra-proceso (monotonía local):**
   Si dos eventos $e_1$ y $e_2$ ocurren dentro del mismo servidor y $e_1$ antecede a $e_2$, la Regla 1 garantiza que $L(e_1) < L(e_2)$ porque `incrementAndGet()` o `updateAndGet()` son estrictamente crecientes. En la salida verificada:
   ```
   Servidor Server-A : Monotonía interna estrictamente creciente -> SÍ (CORRECTO)
   Servidor Server-B : Monotonía interna estrictamente creciente -> SÍ (CORRECTO)
   Servidor Server-C : Monotonía interna estrictamente creciente -> SÍ (CORRECTO)
   ```

2. **Causalidad inter-proceso (envío $\to$ recepción):**
   Un mensaje no puede recibirse antes de haberse enviado. La Regla 2 garantiza matemáticamente que:
   $$L(\text{receive}) = \max(L_{\text{local}}, L_{\text{send}}) + 1 \ge L_{\text{send}} + 1 > L_{\text{send}}$$
   Por lo tanto, $L(\text{send}) < L(\text{receive})$ se cumple siempre, sin importar la velocidad relativa de los hilos ni las demoras en la red.

#### Ejemplos concretos observados en `salida.txt`:

- **Avance por salto lógico tras recepción:**
  `Server-C` envía el mensaje `#9052` en su tiempo lógico $L=3$. Cuando `Server-B` (cuyo reloj local iba en $L=5$) recibe el mensaje, su reloj se ajusta a $\max(5, 3) + 1 = 6$.
- **Propagación de causalidad transitoria:**
  `Server-B` envía el mensaje `#9043` con $L=9$. Al recibirlo `Server-C` (cuyo reloj local iba en $L=4$), su reloj da un salto directo a $\max(4, 9) + 1 = 10$. El reloj de `Server-C` absorbe el conocimiento del progreso causal de `Server-B`.

Cualquier observador del log global puede construir un ordenamiento topológico válido de los eventos respetando todas las dependencias causales reales del sistema.

### Compilación y Ejecución

Para compilar y volver a ejecutar la simulación:

```bash
cd lamport
javac LamportSimulator.java
java LamportSimulator
```

---

#### Fuentes

- Lamport, L. (1978). *Time, Clocks, and the Ordering of Events in a Distributed System*. Communications of the ACM, 21(7), 558-565.
- Apache Kafka Documentation: *Kafka Core Concepts & Topic Partitions*.
- Apache Kafka Streams: *Event Time and Timestamps*.
- AWS DynamoDB Developer Guide: *Effective Data Sorting with Amazon DynamoDB and Sort Keys*.
- Google Cloud Spanner Architecture: *Spanner: TrueTime and External Consistency*.
- Google Research (Corbett et al., 2012): *Spanner: Google’s Globally-Distributed Database*.

###### By Brent Pérez Paitán
