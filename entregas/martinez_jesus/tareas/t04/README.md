# Ordenamiento de tiempo de diferentes tecnologías

En una base de datos que se ejecuta en un solo servidor, saber qué transacción ocurrió primero es sencillo porque basta con consultar un contador local en memoria o el reloj de esa única máquina. Sin embargo, cuando una base de datos se distribuye en múltiples servidores o a través de distintos continentes, no existe un reloj físico universal instantáneo. Los cristales de cuarzo que miden la hora en cada servidor se adelantan o se atrasan ligeramente, fenómeno conocido como clock drift, y los protocolos de sincronización por red tienen retrasos variables.

Si el Servidor A tiene el reloj adelantado y escribe un dato, y un instante después el Servidor B tiene el reloj atrasado y modifica ese mismo dato o uno relacionado, un sistema que confíe ciegamente en la hora local creerá erróneamente que el cambio del Servidor B ocurrió antes que el del Servidor A.

Para resolver este problema fundamental, **Google Spanner (TrueTime)**, **CockroachDB** y **Oracle Database** adoptan tres filosofías arquitectónicas completamente distintas:

1. **Google Spanner (TrueTime):** Mide el tiempo físico real acotando el margen de error con hardware especializado (relojes atómicos y GPS) y espera deliberadamente al escribir (Commit Wait) hasta que esa incertidumbre haya pasado.
2. **CockroachDB:** Combina el reloj físico de servidores estándar con un contador lógico (Reloj Lógico Híbrido o HLC); no espera al escribir, pero reinicia las lecturas (Read Restart) cuando encuentra datos dentro de una ventana de duda temporal y apaga el nodo si su reloj se desfasa demasiado.
3. **Oracle Database:** Prescinde por completo del reloj físico de pared para ordenar sus datos y utiliza un número de secuencia lógico puro (System Change Number o SCN) en memoria, el cual difunde por red al confirmar cada transacción.

## 1. Google Spanner y TrueTime

### 1.1. ¿Qué es y para qué se usa?

Google Spanner es la base de datos relacional (SQL) distribuida globalmente, escalable y tolerante a fallos creada por Google. Para escalar horizontalmente, Spanner divide las tablas en fragmentos o particiones llamados tablets, y replica cada tablet de manera síncrona a través de múltiples zonas o regiones geográficas mediante un grupo de consenso Paxos independiente. Además, utiliza Control de Concurrencia Multiversión (MVCC), lo que significa que cuando un dato se actualiza, no se sobrescribe en el mismo lugar, sino que se guarda una nueva versión inmutable etiquetada con su marca de tiempo (timestamp).

TrueTime es el servicio y API de sincronización de relojes distribuidos que Google construyó específicamente para sustentar a Spanner. Se utiliza para asignar marcas de tiempo a las transacciones de forma que reflejen el orden real en que ocurrieron en el mundo físico, sin que los servidores ubicados en distintos continentes tengan que enviarse mensajes entre sí solo para acordar un número de secuencia global. Gracias a TrueTime y MVCC, Spanner permite ordenar transacciones de lectura-escritura globalmente respetando la línea de tiempo real y ejecutar lecturas con una fotografía exacta de toda la base de datos (incluso abarcando múltiples regiones) en una sola ronda de comunicación y sin bloquear ni ser bloqueado por las escrituras en curso.

Además de la versión en la nube pública de Google, existe Spanner Omni para infraestructuras autogestionadas donde no hay relojes atómicos ni antenas GPS. En Spanner Omni, TrueTime se implementa por software designando a un servidor de base de datos del clúster como servidor de tiempo primario (Time server) y ejecutando demonios clientes (Time clients) en cada máquina host, con promoción automática de otro servidor si el primario falla.

### 1.2. ¿Qué tiempo usa para ordenar?

Spanner utiliza un reloj físico de pared con incertidumbre explícitamente acotada, combinado con una regla de asignación monotónica en los líderes de cada grupo Paxos.

Para entenderlo de forma sencilla,: cuando un programa normal pide la hora al sistema operativo recibe un número exacto, ocultando que ese reloj puede estar adelantado o atrasado respecto a otra máquina. TrueTime hace lo contrario, expone la duda del reloj de forma matemática. Cuando Spanner llama a la función `TT.now()`, TrueTime no devuelve un punto en el tiempo, sino un intervalo de tiempo físico $[t_{\text{earliest}}, t_{\text{latest}}]$. TrueTime garantiza que el tiempo físico absoluto real ($t_{\text{abs}}$) en el que se hizo la llamada está encerrado dentro de ese intervalo:

$$t_{\text{earliest}} \le t_{\text{abs}} \le t_{\text{latest}}$$

La mitad del ancho de ese intervalo se denomina incertidumbre o margen de error ($\epsilon$), de modo que el ancho total del intervalo es $2\epsilon$:

$$t_{\text{latest}} - t_{\text{earliest}} = 2\epsilon$$

**¿Cómo convierte Spanner este intervalo en un número único para ordenar una escritura?**
El nodo que actúa como líder del grupo Paxos asigna a la transacción una marca de tiempo de confirmación ($\text{commit\_ts}$) cumpliendo dos reglas:

1. **Regla física:** El $\text{commit\_ts}$ debe ser al menos igual al extremo superior del intervalo actual: $\text{commit\_ts} := \text{TT.now().latest}$.
2. **Regla monotónica:** El $\text{commit\_ts}$ debe ser estrictamente mayor que cualquier marca de tiempo que ese líder Paxos haya asignado a transacciones anteriores.

Adicionalmente, cada réplica Paxos mantiene un registro llamado tiempo seguro ($t_{\text{safe}}$), que se calcula como el mínimo entre la última marca aplicada por la máquina de estados Paxos ($t_{\text{Paxos}}$, que avanza monotónicamente con escrituras y latidos) y la marca de preparación más antigua de transacciones pendientes en dos fases ($t_{\text{TM}}$). Cualquier réplica puede responder lecturas para un instante $t$ sin consultar al líder siempre que se cumpla $t \le t_{\text{safe}}$.

### 1.3. ¿Qué orden garantiza y hasta dónde?

Spanner garantiza por defecto **Consistencia Externa (*External Consistency*)**, también conocida en la teoría de sistemas distribuidos como **Serializabilidad Estricta (*Strict Serializability*)**, en **todo el sistema a escala global**.

Para dimensionar qué significa esto y hasta dónde llega, conviene distinguir tres conceptos:

* **Serializabilidad clásica:** Garantiza que la ejecución concurrente de varias transacciones produzca el mismo resultado que algún orden secuencial (una tras otra), pero no exige que ese orden respete el reloj del mundo real. Si la transacción $T_1$ termina a las `10:00` en México y la transacción $T_2$ empieza a las `10:01` en Tokio sobre otra tabla distinta, un sistema simplemente serializable podría ordenar $T_2$ antes que $T_1$ sin violar la serializabilidad.
* **Linealización (*Linearizability*):** Exige que las operaciones respeten el orden del tiempo real, pero se define únicamente para operaciones atómicas individuales sobre un solo objeto o llave.
* **Consistencia Externa (Serializabilidad Estricta):** Es la unión de ambas propiedades. Garantiza que las transacciones (incluso aquellas que leen y modifican múltiples filas, tablas y particiones en distintas partes del mundo) se ordenen de forma equivalente a una ejecución en serie cuyo orden coincide exactamente con el tiempo real de confirmación.

En Spanner, esta garantía abarca todas las llaves, filas, tablas, particiones (*tablets*), grupos Paxos, servidores y regiones geográficas del planeta.Esto garantiza que si una transacción $T_1$ termina de confirmarse antes de que otra transacción $T_2$ empiece a confirmarse en el mundo real, el sistema garantiza que $\text{commit\_ts}(T_2) > \text{commit\_ts}(T_1)$. Ningún cliente en ninguna parte del mundo verá los efectos de $T_2$ sin ver también los efectos de $T_1$, incluso si $T_1$ y $T_2$ modificaron datos sin ninguna relación en servidores que nunca se comunicaron.

¿Cómo mantiene este orden cuando las transacciones sí se cruzan?

* **Dentro de una misma partición (*tablet*):** Si dos transacciones intentan modificar o leer y escribir los mismos datos al mismo tiempo, el líder local utiliza Bloqueo de Dos Fases (Two-Phase Locking o 2PL) para ponerlas en fila y asegurar que la segunda obtenga un $\text{commit\_ts}$ mayor que la primera.
* **A través de múltiples particiones (varios grupos Paxos):** Cuando una transacción modifica datos que viven en distintos grupos Paxos, Spanner ejecuta el protocolo de Confirmación en Dos Fases (*Two-Phase Commit* o 2PC). Cada líder participante adquiere sus bloqueos locales y asigna un timestamp de preparación ($s_{\text{prep}}$). Luego, el líder coordinador elige un $\text{commit\_ts}$ global que es estrictamente mayor que todos los $s_{\text{prep}}$ de los participantes, mayor que el $\text{TT.now().latest}$ del momento en que recibió la orden de confirmación, y mayor que cualquier marca asignada previamente por ese coordinador.

### 1.4. ¿Qué pasa cuando los relojes no coinciden?

Spanner asume desde su diseño que los relojes de los servidores nunca coinciden de forma perfecta y resuelve esa discrepancia combinando sincronización por hardware con una pausa obligatoria llamada Espera de Confirmación (Commit Wait):

1. **Sincronización con receptores GPS y relojes atómicos:** En lugar de usar servidores NTP convencionales de Internet, Google instala en cada centro de datos máquinas maestras de tiempo (*Time Masters*) equipadas con **receptores GPS** de antena dedicada y **relojes atómicos** (*Armageddon masters*). Se utilizan ambas tecnologías porque sus fallos son independientes: los receptores GPS pueden fallar por problemas de antena o interferencia de radiofrecuencia, mientras que los relojes atómicos no necesitan señal satelital pero sufren una deriva gradual mínima. Cada servidor de Spanner tiene un demonio de tiempo en segundo plano que consulta cada $30\text{ segundos}$ a varios maestros de tiempo (tanto en su propio centro de datos como en centros cercanos) para corregir su reloj de cuarzo local y minimizar su error $\epsilon$.
2. **Crecimiento conservador de la incertidumbre entre sincronizaciones:** Mientras transcurren los $30\text{ segundos}$ entre una sincronización y la siguiente, el servidor asume el peor escenario posible de desajuste de su cristal de cuarzo y va creciendo su intervalo $\epsilon$ de manera continua.
3. **Espera de Confirmación (*Commit Wait*):**
Evita que el orden de las transacciones pueda corromperse siguiendo tres pasos estrictos al escribir:

* **Paso 1:** El líder Paxos elige $\text{commit\_ts} := \text{TT.now().latest}$.
* **Paso 2:** Inicia la replicación de la escritura hacia las réplicas del grupo Paxos para alcanzar quórum.
* **Paso 3:** Aun cuando las réplicas ya hayan respondido, el líder retiene los bloqueos exclusivos y se queda esperando hasta que una nueva llamada a TrueTime cumpla que el extremo más temprano posible del reloj que ya superó la marca de confirmación:
     $$\text{TT.now().earliest} > \text{commit\_ts}$$

   Como $t_{\text{abs}} \ge \text{TT.now().earliest}$, en el momento en que $\text{TT.now().earliest} > \text{commit\_ts}$, Spanner tiene la certeza física absoluta de que el instante $\text{commit\_ts}$ ya quedó en el pasado real ($t_{\text{abs}} > \text{commit\_ts}$) antes de que los bloqueos se liberen y el cliente reciba el aviso de éxito. Por lo tanto, cualquier transacción $T_2$ que empiece después en cualquier otra parte del mundo obtendrá forzosamente un $\text{TT.now().latest} \ge t_{\text{abs}} > \text{commit\_ts}(T_1)$, respetando el orden real sin que los dos servidores hayan tenido que hablar entre sí.

4. **Solapamiento con la red y ralentización si el error aumenta:** Dado que esta espera dura en promedio $2\epsilon$ (menos de $1\text{ ms}$ en la infraestructura actual de Google Cloud) y se ejecuta en paralelo con el viaje de red del consenso Paxos entre zonas (que toma varios milisegundos), en condiciones normales el Commit Wait queda oculto dentro del tiempo de red y no añade latencia extra. Sin embargo, si fallan los relojes maestros o hay problemas de red que impidan sincronizar, $\epsilon$ crece. En ese escenario, Spanner no ignora el problema ni apaga el nodo sino que alarga el tiempo de espera del Commit Wait y se vuelve más lento. Como los bloqueos exclusivos se retienen durante toda esa espera, un aumento de $\epsilon$ en filas muy disputadas encola a otras transacciones y eleva la latencia del percentil 99.

## 2. CockroachDB

### 2.1. ¿Qué es y para qué se usa?

**CockroachDB** es una base de datos SQL distribuida nativa de la nube (NewSQL) diseñada desde cero para ejecutarse sobre hardware convencional (commodity hardware) en cualquier nube pública, privada o centro de datos propio, sin depender de relojes atómicos ni receptores GPS. Se utiliza para aplicaciones transaccionales que requieren garantías ACID, alta disponibilidad automática ante caídas de nodos o regiones y escalabilidad horizontal.

Internamente, CockroachDB divide todas las tablas e índices en fragmentos contiguos de espacio de llaves llamados ranges y replica cada range entre varios nodos utilizando el protocolo de consenso Raft. Dentro de cada grupo Raft, un nodo específico actúa como titular de la concesión (leaseholder), encargándose de coordinar las escrituras (que primero se asientan como intenciones de escritura provisionales o write intents replicadas vía Raft) y de servir las lecturas consistentes a partir de múltiples versiones almacenadas mediante MVCC.

### 2.2. ¿Qué tiempo usa para ordenar?

CockroachDB utiliza un Reloj Lógico Híbrido (Hybrid Logical Clock o HLC) para etiquetar todas sus transacciones y versiones de datos MVCC. El algoritmo HLC fue diseñado en 2014 por Kulkarni, Demirbas y colegas para unir lo mejor de dos mundos en sistemas que solo cuentan con sincronización NTP por software:

* **¿Por qué no basta un reloj lógico de Lamport puro?** Un reloj de Lamport es un simple contador numérico que sube con cada mensaje entre nodos. Ordena perfectamente quién causó a quién, pero no tiene ninguna relación con la hora real del día. Con un reloj de Lamport no se le pueden hacer peticiones a la base de datos como el mostrar cómo estaban los registros a cierta hora.
* **¿Por qué no basta el reloj físico de pared del servidor?** Porque sincronizar servidores por software mediante NTP deja márgenes de error de decenas o cientos de milisegundos, y además el reloj del sistema operativo puede dar saltos hacia atrás al ajustarse.

El **Reloj Lógico Híbrido (HLC)** resuelve ambas limitaciones representando cada marca de tiempo como un par compuesto **$(l, c)$** que se ordena lexicográficamente (primero se compara $l$ y, si hay empate, se compara $c$):

1. **Componente físico ($l$):** Guarda el valor de tiempo físico de pared (wall time) más alto que este nodo haya conocido hasta ese momento, ya sea leyéndolo de su propio reloj físico local ($pt$) o habiéndolo visto dentro de un mensaje que le envió otro nodo del clúster.
2. **Componente lógico ($c$):** Es un número entero contador que sirve exclusivamente para **desempatar** eventos cuando el tiempo físico $l$ no ha cambiado. Esto ocurre en dos casos: cuando suceden varios eventos dentro del mismo milisegundo/microsegundo local, o cuando el nodo recibe un mensaje de otro servidor cuyo reloj físico iba más adelantado ($l > pt$), obligando al nodo local a mantener ese $l$ adelantado e incrementar $c$ ($c = c + 1$) hasta que su propio reloj físico $pt$ por fin lo alcance. En cuanto el reloj físico local $pt$ supera a $l$, el valor de $l$ se actualiza a $pt$ y el contador lógico $c$ se reinicia a $0$.

Gracias a esta estructura $(l, c)$, el HLC garantiza tres propiedades simultáneas:

* **Monotonicidad estricta:** El reloj HLC jamás retrocede, incluso si el reloj físico del sistema operativo da un salto hacia atrás ($\text{HLC} \ge pt$).
* **Cercanía al tiempo físico real:** Permite hacer consultas históricas por tiempo del mundo real y recolecciones de basura basadas en tiempo.
* **Causalidad unidireccional de Lamport:** Si un evento $A$ se comunica con otro nodo y causa el evento $B$, el sistema garantiza matemáticamente que $\text{HLC}(A) < \text{HLC}(B)$.

### 2.3. ¿Qué orden garantiza y hasta dónde?

Por defecto, CockroachDB opera en el nivel de aislamiento SERIALIZABLE (ofreciendo también READ COMMITTED, donde cada sentencia individual lee una instantánea consistente y los bloqueos se replican por Raft) combinado con la garantía de ausencia de lecturas obsoletas (no stale reads).

Para entender con exactitud qué garantiza y hasta dónde llega su frontera se presentan las siguientes características:

* **En todo el sistema (Alcance Global):** Garantiza Serializabilidad. Todas las transacciones del clúster son equivalentes a algún orden secuencial estricto, impidiendo por completo anomalías clásicas como lecturas sucias, lecturas no repetibles, lecturas fantasma, escrituras perdidas o write skew.
* **Dentro de una misma llave, fila o conjunto de llaves superpuestas (Linealización por llave):** Garantiza orden en tiempo real (no stale reads). Si la transacción $T_1$ modifica la fila $X$ y confirma, y un milisegundo después la transacción $T_2$ lee la fila $X$ desde cualquier nodo del mundo, $T_2$ verá garantizadamente el valor nuevo escrito por $T_1$. Esto se logra porque todas las lecturas y escrituras sobre una misma llave pasan por el leaseholder de ese rango y consultan su caché de marcas de lectura (timestamp cache) en memoria.
* **El límite de su garantía (Llaves completamente disjuntas sin comunicación interna):** A diferencia de Spanner, CockroachDB no garantiza consistencia externa global entre transacciones que tocan llaves completamente distintas en nodos con relojes desfasados, lo que permite una anomalía específica llamada inversión causal (causal reverse).

  * *Ejemplo de inversión causal:* Supongamos que el Nodo 1 tiene el reloj en $150\text{ ms}$ y el Nodo 2 tiene el reloj atrasado en $100\text{ ms}$. Una transacción $T_1$ escribe en la tabla ` Cuentas ` (llave $A$) conectándose al Nodo 1 y se confirma con marca de tiempo $150\text{ ms}$. Inmediatamente después, el usuario llama por teléfono a otro usuario (comunicación externa fuera de la base de datos) y este ejecuta la transacción $T_2$ insertando un registro en una tabla independiente ` Bitacora ` (llave $B$, sin leer $A$ ni tener llaves foráneas hacia $A$) conectándose al Nodo 2. Como el Nodo 2 no tocó la llave $A$, usa su reloj local y confirma $T_2$ con marca de tiempo $100\text{ ms}$. En el mundo real $T_1$ ocurrió antes que $T_2$, pero en la historia de la base de datos $T_2$ ($100\text{ ms}$) quedó ordenada antes que $T_1$ ($150\text{ ms}$). Si alguien ejecuta una consulta histórica o un backup a los $120\text{ ms}$, verá el efecto de $T_2$ sin ver su causa $T_1$.
  * *Cómo mitigarlo si se requiere:* Si existe comunicación externa entre clientes, CockroachDB permite que el primer cliente extraiga un token de causalidad (causality token) con el timestamp máximo observado y se lo pase al segundo cliente para forzar a que la segunda transacción inicie con un reloj al menos igual al de la primera.

### 2.4. ¿Qué pasa cuando los relojes no coinciden?

CockroachDB no usa simplemente el *Commit Wait* de Spanner para evitar la inversión causal porque con sincronización por software (NTP), el margen de error máximo permitido entre servidores es de cientos de milisegundos (por defecto $500\text{ ms}$). Si cada escritura tuviera que quedarse pausada esperando $500\text{ ms}$ o $1000\text{ ms}$ antes de confirmar, el rendimiento de escritura de la base de datos caería drásticamente.

Por ello, CockroachDB permite que las escrituras confirmen sin esperar al reloj y resuelve las discrepancias de tiempo mediante **cuatro mecanismos defensivos**:

1. **Sincronización con NTP (`chrony`) y suavizado de segundos intercalares (*Leap Second Smearing*):** CockroachDB exige que todos los servidores sincronicen sus relojes físicos mediante software NTP moderno, recomendando específicamente **`chrony`** en Linux. Además, requiere que los servidores NTP utilicen suavizado de segundos intercalares, como Amazon Time Sync Service o el servicio NTP de Google recomendando usar el NTP de Google incluso cuando se despliega en Microsoft Azure, para diluir gradualmente cualquier ajuste de segundo intercalar sin provocar saltos bruscos en el reloj.
2. **Desempate y empuje del HLC en cada mensaje de red:** Cada vez que un nodo A envía un mensaje a un nodo B, adjunta su marca HLC actual $(l_m, c_m)$. Al recibirlo, el nodo B actualiza su propio reloj calculando:
   $$l_{\text{nuevo}} = \max(l_{\text{local}}, l_m, pt_{\text{local}})$$
   y ajusta su contador lógico $c$ para que su nueva marca sea estrictamente mayor que la suya anterior y que la recibida en el mensaje. De este modo, si un nodo tiene el reloj físico atrasado, el tráfico normal de red con otros nodos **empuja su reloj HLC hacia adelante** e impide que asigne marcas en el pasado respecto a eventos con los que ya interactuó.
3. **Intervalo de Incertidumbre, Reinicio de Lecturas (*Read Restarts*) y Empuje de Escrituras:**
   Aquí es donde CockroachDB paga el precio de la incertidumbre del reloj: en las lecturas que encuentran datos dudosos.
   * CockroachDB configura un desfase máximo tolerado entre nodos llamado `max-offset`, cuyo valor por defecto es **$500\text{ ms}$**.
   * Supongamos que una transacción lectora inicia en un nodo cuyo reloj está atrasado y recibe la marca de lectura $\text{reader\_ts} = 100\text{ ms}$. Como los demás relojes del clúster podrían estar adelantados hasta por el valor de `max-offset` ($500\text{ ms}$), para esta transacción existe un **Intervalo de Incertidumbre**:
     $$[\text{reader\_ts}, \text{reader\_ts} + \text{max-offset}] = [100\text{ ms}, 600\text{ ms}]$$
   * Cualquier versión de un dato que tenga una marca de tiempo menor o igual a $100\text{ ms}$ ocurrió definitivamente en el pasado del lector (se debe leer). Cualquier versión con marca mayor a $600\text{ ms}$ ocurrió definitivamente en el futuro del lector (se debe ignorar).
   * **¿Qué pasa si al leer una fila encuentra una versión escrita con marca $T_v = 120\text{ ms}$ ($\text{reader\_ts} < T_v \le \text{reader\_ts} + \text{max-offset}$)?** El lector no tiene forma de saber si esa escritura de $120\text{ ms}$ ocurrió realmente *después* en el mundo físico o si ocurrió *antes* en un servidor que tenía el reloj $20\text{ ms}$ adelantado.
   * Ante esa duda, para no devolver datos antiguos, CockroachDB dispara un Reinicio de Lectura por Incertidumbre (Read Within Uncertainty Interval / Read Restart) en donde se aborta la lectura actual, adelanta la marca de tiempo de la transacción lectora justo por encima de la versión encontrada ($\text{reader\_ts}_{\text{nuevo}} = T_v$, manteniendo fijo el techo original de incertidumbre $\text{reader\_ts}_{\text{inicial}} + \text{max-offset}$, lo que encoge la ventana de duda restante) y vuelve a ejecutar la lectura. Si es una consulta simple de una sola sentencia, el propio servidor hace el reintento de forma transparente (convirtiendo una lectura de $2\text{ ms}$ en $4\text{ ms}$); si es una transacción multi-sentencia que ya devolvió datos previos al cliente, lanza un error de reintento al cliente.
   * De forma complementaria, si llega una escritura con un timestamp menor o igual al registrado en el timestamp cache de lecturas previas sobre esa llave (o menor o igual al closed timestamp que el leaseholder promete por Raft para permitir lecturas históricas en réplicas follower reads), CockroachDB empuja (push) el timestamp de la escritura hacia adelante y realiza un refresco de lectura (read refreshing), reiniciando la transacción solo si los datos que había leído cambiaron en ese lapso.
4. **Autoterminación del nodo (*Self-Termination*) al superar el $80\%$ de `max-offset`:** Los nodos del clúster vigilan constantemente el desfase de sus relojes físicos entre sí. Si un nodo detecta que su reloj físico se ha desviado más del **$80\%$ del `max-offset`** respecto a al menos la mitad de los nodos conocidos del clúster (es decir, más de **$400\text{ ms}$** cuando `max-offset` es $500\text{ ms}$), el nodo se apaga a sí mismo de inmediato (self-terminates / crashes). Es una medida de seguridad estricta donde CockroachDB prefiere sacrificar la disponibilidad de un servidor descalibrado antes que permitir que viole el límite de `max-offset` y corrompa el orden de los datos.

## 3. Oracle Database

### 3.1. ¿Qué es y para qué se usa?

**Oracle Database** (incluyendo versiones empresariales ampliamente desplegadas como 19c y Oracle AI Database 26ai) es un sistema de gestión de bases de datos relacional diseñado para cargas transaccionales críticas (OLTP), almacenes de datos analíticos y cargas mixtas, proporcionando garantías ACID, recuperación ante fallos de instancia o disco y lecturas consistentes.

A nivel de arquitectura, Oracle puede desplegarse en tres modalidades principales:
1. **Instancia Única (*Single Instance*):** Un solo servidor gestiona los archivos de la base de datos a través de su memoria compartida (System Global Area) y procesos de fondo como el escritor de registros (LOg Writer).
2. **Clúster de Bases de Datos Activas (**Oracle Real Application Clusters o RAC**):** Múltiples instancias ejecutándose en distintos servidores abren y modifican simultáneamente la **misma base de datos compartida en almacenamiento común**, coordinando sus memorias caché a través de una red privada de alta velocidad mediante la tecnología Cache Fusion.
3. **Bases de Datos Distribuidas:** Múltiples bases de datos Oracle completamente independientes que se comunican entre sí a través de la red mediante enlaces de base de datos (Database Links) para ejecutar consultas remotas o transacciones distribuidas.

### 3.2. ¿Qué tiempo usa para ordenar?
Oracle Database utiliza un reloj lógico puro / número de secuencia monotónico asignado internamente llamado SCN (System Change Number).

A diferencia de Spanner y CockroachDB, Oracle no utiliza el reloj físico de pared del sistema operativo para ordenar transacciones, versiones de filas ni eventos de recuperación. El SCN es un número entero interno que vive en la memoria compartida (SGA) y se incrementa de manera estrictamente monotónica ($1, 2, 3\dots$) cada vez que ocurren cambios y confirmaciones en la base de datos.

Para comprender cómo el SCN actúa como el reloj interno de todo el motor de Oracle, veamos cómo interviene en cada operación:

* **En las escrituras y confirmaciones (`COMMIT`):** Cuando una transacción modifica una fila en un bloque de datos, deja una marca con su identificador de transacción (XID) en la cabecera del bloque (en la lista de transacciones interesadas o Interested Transaction List) y guarda la imagen anterior del dato en un segmento de deshacer (UNDO). Cuando el usuario ejecuta `COMMIT`, Oracle incrementa su contador y le asigna un **SCN de confirmación (*commit SCN*)** en la tabla de transacciones del segmento de UNDO, y el proceso LGWR escribe inmediatamente el registro de confirmación con ese SCN en el archivo de registro en disco (Online Redo Log). Varias transacciones que confirman exactamente al mismo tiempo pueden recibir el mismo SCN (optimización de batching visible en funciones internas del kernel), indicando que ocurrieron en el mismo instante lógico. Además, para que el `COMMIT` sea instantáneo (fast commit), Oracle no pierde tiempo visitando todos los bloques modificados en ese momento. De esta manera, si una lectura posterior se encuentra con un bloque que aún tiene la entrada ITL abierta de una transacción que ya confirmó, el lector mira el segmento de UNDO, descubre el commit SCN y limpia el bloque sobre la marcha (delayed block cleanout).
* **En las lecturas consistentes (*Read Consistency*) sin bloqueos:** Cuando se lanza una consulta SQL, Oracle toma el número actual del contador y se lo asigna a la consulta como su SCN de consulta (Query SCN). La regla es simple: la consulta solo puede ver datos cuyo SCN sea menor o igual a su $\text{query SCN}$. Si mientras escanea una tabla la consulta encuentra un bloque que fue modificado después (tiene un SCN mayor al $\text{query SCN}$ o tiene cambios sin confirmar), la consulta no se bloquea ni se reinicia: clona el bloque en memoria privada y le aplica los registros de UNDO hacia atrás como una máquina del tiempo hasta reconstruir exactamente cómo se veía ese bloque en el instante del $\text{query SCN}$. Este mismo mecanismo permite hacer consultas históricas (Flashback Query).
* **En la recuperación ante fallos (*Recovery*):** Cada bloque de datos y cada cabecera de archivo registran el SCN de sus últimos cambios y puntos de control (checkpoint SCNs), lo que le permite a Oracle saber exactamente desde qué punto del *redo log* debe rehacer operaciones si el servidor se apaga de golpe.
* **Mapeo aproximado al reloj humano:** Como el SCN es solo una secuencia lógica, Oracle guarda periódicamente en una tabla interna del sistema una equivalencia aproximada entre el SCN y la hora del reloj de pared para que los usuarios puedan usar la función `SCN_TO_TIMESTAMP()` (con una precisión aproximada de unos $3\text{ segundos}$), pero esa hora física nunca se usa para ordenar transacciones.

### 3.3. ¿Qué orden garantiza y hasta dónde?
El SCN garantiza un orden lógico monotónico total y lecturas consistentes (donde las lecturas jamás bloquean a las escrituras y las escrituras jamás bloquean a las lecturas). En su nivel de aislamiento por defecto (`READ COMMITTED`), cada sentencia SQL individual ve una fotografía consistente al query SCN del momento en que arrancó esa sentencia; en los niveles `SERIALIZABLE` o `READ ONLY`, todas las sentencias de la transacción ven la fotografía correspondiente al SCN en que inició toda la transacción.

El alcance espacial de este orden depende de cómo esté desplegada la base de datos:
1. **En Instancia Única (En toda la base de datos):** Garantiza un **orden total en todo el sistema**. Como solo hay una memoria compartida SGA, todos los procesos, tablas y particiones leen e incrementan el mismo contador SCN centralizado.
2. **En un clúster Oracle RAC (En todo el clúster):** Garantiza un orden total en todo el clúster. Todas las instancias que abren la base de datos comparten un único espacio o dominio global de SCN. Bajo el mecanismo moderno por defecto (Broadcast on Commit), el SCN de cada transacción confirmada se propaga inmediatamente a todos los servidores del clúster antes de dar por concluido el `COMMIT`, garantizando que cualquier consulta posterior en cualquier otro nodo del clúster vea de inmediato los cambios confirmados.
3. **Entre bases de datos independientes conectadas por *Database Links*:** Cada base de datos independiente tiene su propio contador SCN particular, por lo que dos bases que no hablan entre sí llevan números distintos. Sin embargo, en el momento en que una consulta o transacción cruza un Database Link, Oracle coordina y sincroniza los SCNs entre los nodos que se comunican al abrir la conexión, al ejecutar cada sentencia SQL distribuida y al confirmar mediante Two-Phase Commit (2PC), garantizando consistencia de lectura distribuida global (a nivel de sentencia y de transacción) y permitiendo recuperaciones coordinadas entre múltiples bases.

### 3.4. ¿Qué pasa cuando los relojes no coinciden?

Para responder qué ocurre cuando los relojes no coinciden en Oracle Database, debemos separar el reloj lógico interno (SCN) del reloj físico de pared del sistema operativo:

1. **Los desajustes del reloj físico del servidor se ignoran para el orden de los datos:** Dado que toda la visibilidad de las filas, el orden de los commits y la verificación de cero pérdida de datos se rigen única y exclusivamente por el número entero SCN, si el reloj de pared de Linux o Windows se adelanta o se atrasa, el motor transaccional lo ignora por completo y los datos no sufren ninguna corrupción ni desorden.

2. **¿Qué pasa cuando los contadores SCN de distintos nodos en un clúster Oracle RAC no coinciden? (*Broadcast on Commit* y RDMA):**
   En un clúster RAC hay varios servidores independientes y cada instancia guarda su copia del SCN actual en su propia memoria SGA. Si el Nodo 1 hace muchos cambios, su SCN sube, para que los demás nodos no se queden con un SCN viejo se utilizan 3 métodos:
   * **El método histórico (Esquema SCN de Lamport en Oracle 9i y 10gR1):** Antiguamente, Oracle usaba un esquema de reloj lógico de Lamport donde los nodos adjuntaban su número SCN dentro de los mensajes normales de transferencia de bloques del servicio de caché global (GCS/GES). Si un nodo no intercambiaba mensajes en un rato, su SCN podía quedarse rezagado respecto a otro nodo hasta un límite controlado por el parámetro `MAX_COMMIT_PROPAGATION_DELAY`, cuyo valor por defecto era de $700\text{ centésimas de segundo}$ ($7\text{ segundos}$).
   * **El método estándar actual (*Broadcast on Commit* o BOC desde Oracle 10gR2):** A partir de Oracle 10g Release 2 y en todas las versiones actuales, Oracle eliminó esa ventana de retraso depreciando `MAX_COMMIT_PROPAGATION_DELAY` (fijándolo efectivamente en $0\text{ segundos}$) y activó por defecto Broadcast on Commit (BOC). Bajo este esquema, cada vez que una sesión ejecuta `COMMIT` en el Nodo 1, el proceso **LGWR** escribe en el *redo log* local, incrementa el SCN y envía asíncronamente una orden a los procesos LMS (Lock Manager Server de Global Cache Services) para que difundan de inmediato el nuevo SCN a todas las demás instancias del clúster. Mientras LGWR espera a que termine su escritura en disco (log file parallel write), también espera a recibir la confirmación de que todos los demás nodos actualizaron su SCN en memoria (evento de espera wait for scn ack, el cual escribe una alerta en los archivos de traza si tarda más de $500\text{ ms}$) antes de responder al usuario que el `COMMIT` fue exitoso.
   * **Aceleración por hardware (*Broadcast-on-Commit over RDMA* en Oracle Exadata):** En sistemas de alto rendimiento Oracle Exadata (incluyendo Oracle AI Database 26ai), depender de que el sistema operativo despierte al proceso LMS en los nodos remotos puede añadir latencia si hay mucha carga de CPU o cambios de contexto. Por ello, Oracle introdujo Broadcast-on-Commit over RDMA, que reemplaza los mensajes de red tradicionales por operaciones atómicas de Acceso Directo a Memoria Remota (RDMA) que cuando una transacción confirma, el nodo actualiza directamente el SCN en la memoria RAM de las demás instancias del clúster a través de la tarjeta de red, sin pasar por el proceso LMS ni interrumpir al procesador remoto.
3. **¿Qué pasa cuando dos bases de datos independientes tienen SCNs distintos en una transacción distribuida? (*Two-Phase Commit* - 2PC):**
   Imaginemos que la Base de Datos A (cuyo SCN va en `1,000`) y la Base de Datos B (cuyo SCN va en `5,000,000`) participan en una misma transacción distribuida a través de un *Database Link*. ¿Cómo empatan sus relojes lógicos al confirmar?
   * **Fase de Preparación (*Prepare Phase*):** El nodo coordinador global pide a todos los nodos participantes que se preparen para confirmar (volcando sus registros *redo* a disco y reteniendo sus bloqueos locales) y cada nodo responde informando cuál es su SCN local actual. El coordinador compara todos los números recibidos y elige el SCN más alto (highest SCN) de entre todos los nodos involucrados (en nuestro ejemplo, superando los `5,000,000`).
   * **Fase de Confirmación (*Commit Phase*):** La transacción se confirma primero en el sitio de punto de confirmación (Commit Point Site) utilizando ese SCN máximo, y enseguida ese mismo SCN global se envía junto con la orden de confirmación a todos los demás nodos preparados.Al recibirlo, la Base de Datos A da un salto hacia adelante en su contador interno (de `1,000` a más de `5,000,000`) y todas las bases participantes registran el `COMMIT` exactamente con el mismo SCN global. Si un corte de red interrumpe la segunda fase y deja la transacción en duda (in-doubt, bloqueando lecturas/escrituras sobre esas filas con el error `ORA-01591`), el proceso RECO o el administrador pueden forzar la confirmación manual reutilizando exactamente ese SCN acordado mediante el comando `COMMIT FORCE 'id_transaccion', scn`.
4. **¿Qué pasa con el reloj físico del sistema operativo en los servidores de un clúster RAC? (CTSS y NTP):**
   Aunque las tablas y transacciones usan el SCN, el software de infraestructura del clúster (Oracle Clusterware / Grid Infrastructure) sí necesita que los relojes físicos del sistema operativo de los servidores coincidan para gestionar la salud de los nodos y correlacionar archivos de bitácora (logs). Para ello, Oracle incluye su propio demonio de sincronización llamado CTSS (Cluster Time Synchronization Service), iniciado por el proceso `ohasd` donde:
   * Si al arrancar el servidor CTSS detecta que ya existe un demonio NTP (o Windows Time Service) configurado y funcionando en el sistema operativo, CTSS se coloca automáticamente en modo observador (observer mode) y deja que NTP haga el trabajo.
   * Si detecta que ningún servicio NTP está activo en las máquinas, CTSS entra automáticamente en modo activo (active mode), eligiendo un nodo de referencia en el clúster y sincronizando por su cuenta los relojes físicos de todos los servidores del clúster [23, 48].

## Referencias Bibliográficas

### Google Spanner y TrueTime

* Google Cloud Blog – *Strict serializability and external consistency in Spanner*: <https://cloud.google.com/blog/products/databases/strict-serializability-and-external-consistency-in-spanner>
* Documentación oficial de Google Cloud Spanner – *TrueTime and external consistency*: <https://docs.cloud.google.com/spanner/docs/true-time-external-consistency>
* Google Research (Pubs Archive 45855) – *Spanner, TrueTime and the CAP Theorem*: <https://research.google.com/pubs/archive/45855.pdf>

### CockroachDB y Relojes Lógicos Híbridos (HLC)

* Cockroach Labs Engineering Blog – *Clock Management in CockroachDB*: <https://www.cockroachlabs.com/blog/clock-management-cockroachdb/>
* JavaCodeGeeks – *Hybrid Logical Clocks: Time as the Hardest Dependency in a Distributed System*: <https://www.javacodegeeks.com/2026/11/hybrid-logical-clocks-time-as-the-hardest-dependency-in-a-distributed-system.html>
* Cockroach Labs Engineering Blog – *Living Without Atomic Clocks*: <https://www.cockroachlabs.com/blog/living-without-atomic-clocks/>
* Documentación oficial de CockroachDB – *Architecture: Transaction Layer*: <https://docs.cockroachlabs.com/docs/stable/architecture/transaction-layer>

### Oracle Database (SCN, Oracle RAC, 2PC)

* Franck Pachot (DEV Community) – *When did this transaction happen? PostgreSQL snapshots/LSNs, Oracle SCNs, and more*: <https://dev.to/franckpachot/when-did-this-transaction-happen-postgresql-snapshots-lsns-oracle-scns-and-more-1369>

* Documentación oficial de Oracle Database 26ai - <https://docs.oracle.com/en/database/oracle/oracle-database/26/>

* Smanroy Oracle Internals Blog – *Broadcast on Commit and wait for scn ack in Oracle RAC*: <https://smanroy.wordpress.com/tag/broadcast-on-commit/>
* Documentación oficial de Oracle – *Distributed Database Concepts & Two-Phase Commit SCN Coordination*: <https://docs.oracle.com/cd/F49540_01/DOC/server.815/a67784/ds_ch1.htm>
* Oracle Technical White Paper – *Oracle RAC and Exadata in Oracle AI Database 26ai*: <https://www.oracle.com/cmsng/docs/rac-exadata-26ai-v2.pdf>