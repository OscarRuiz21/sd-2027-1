# T04 · ¿Qué reloj usan las tecnologías que conoces?

Asignada en la S10 (24 de octubre) · Entrega: sábado 7 de noviembre de 2026

Nombre: Guerrero López Uriel Iván 

Tecnologías elegidas: Apache Kafka, Apache Cassandra y Google Cloud Spanner.

## Introducción

En clase vimos que el reloj de pared no sirve para ordenar eventos en un sistema distribuido: NTP acerca los relojes, pero nunca los iguala del todo. Por eso existen los relojes lógicos de Lamport y los relojes vectoriales. En la práctica casi nadie los implementa a mano, porque cada tecnología ya trae su propia respuesta a la pregunta ¿en qué me baso para decir qué pasó primero?

Elegí tres tecnologías que responden esa pregunta de tres maneras distintas. Para cada una contesto las cuatro preguntas de la tarea: qué es, qué tiempo usa, qué orden garantiza y qué pasa cuando los relojes no coinciden.

| | Kafka | Cassandra | Spanner |
|---|---|---|---|
| Tipo de tiempo | Número de secuencia (offset) que asigna el líder de la partición | Reloj de pared (timestamp por celda) | Reloj físico con incertidumbre explícita (TrueTime) |
| Orden garantizado | Total dentro de una partición y, por tanto, por llave | Por celda (LWW); linealizable por partición solo con LWT | Global: consistencia externa |
| Relojes desfasados | No afecta al orden; los timestamps son informativos | Pueden perderse escrituras; depende de NTP; desempata en empates | Mide el error y espera (commit wait) |
| Costo principal | Sin orden entre particiones | Riesgo de pérdida silenciosa por desfase de relojes | Hardware especial y latencia de espera |

---

## 1. Apache Kafka

### 1.1 Qué es y para qué se usa

Kafka es una plataforma de streaming de eventos. Los productores escriben mensajes en topics, y cada topic se divide en particiones. Cada partición es un log donde los mensajes solo se agregan al final. Se usa para mover eventos entre servicios (pedidos, clics, pagos), para pipelines de datos y para registrar cambios de otras bases de datos.

### 1.2 Qué tiempo usa para ordenar

Es un número de secuencia que asigna alguien: el broker que es líder de la partición le da a cada mensaje un offset (0, 1, 2, 3…) en el orden en que lo recibe. Ese offset define el orden.

Cada mensaje también lleva un timestamp, pero es solo un dato: puede ser el momento en que el productor creó el mensaje (`CreateTime`, reloj del productor) o el momento en que el broker lo escribió (`LogAppendTime`). Kafka usa ese timestamp para cosas como retención e índices por tiempo, no para ordenar.

```
Topic "pedidos"
Partición 0:  [A:0] [C:1] [A:2] [D:3] [A:4] -->
Partición 1:  [B:0] [B:1] [E:2] [B:3]       -->
Partición 2:  [F:0] [G:1] [F:2]             -->

Letra = llave del mensaje, número = offset que asigna el líder.
```

### 1.3 Qué orden garantiza y hasta dónde

| Alcance | ¿Hay orden? | Detalle |
|---|---|---|
| Dentro de una partición | Sí | Orden total: el de los offsets |
| Mensajes con la misma llave | Sí | La llave se hashea y siempre cae en la misma partición (mientras no cambie el número de particiones) |
| Entre particiones del mismo topic | No | No hay orden entre offsets de particiones distintas |
| En todo el sistema | No | No existe un orden global |

Además, con el productor idempotente (`enable.idempotence`), cada productor numera sus mensajes por partición. Así, si hay reintentos por una falla de red, el broker detecta duplicados y no se desordena lo que el mismo productor mandó.

### 1.4 Qué pasa cuando los relojes no coinciden

Para el orden, nada: el offset lo decide un solo líder por partición, así que no depende de que los relojes de productores y brokers coincidan. Si dos productores tienen relojes distintos, sus timestamps pueden salir desordenados respecto a los offsets, y Kafka mantiene el orden de los offsets.

El desorden solo afecta a lo que sí usa timestamps (buscar por tiempo, borrar por retención), y los brokers tienen configuración para rechazar timestamps demasiado alejados de su propio reloj.

Kafka evita el problema en lugar de resolverlo: un único líder por partición es el punto de ordenamiento, y a cambio no hay orden entre particiones.

---

## 2. Apache Cassandra

### 2.1 Qué es y para qué se usa

Cassandra es una base de datos NoSQL de tipo wide-column, diseñada para alta disponibilidad y escritura masiva. No tiene nodo maestro: todos los nodos forman un anillo y cualquiera puede recibir escrituras. La consistencia es ajustable (`ONE`, `QUORUM`, `ALL`…). Se usa para series de tiempo, mensajería, catálogos y sistemas que no pueden caerse.

### 2.2 Qué tiempo usa para ordenar

Usa reloj físico (de pared). Cada escritura lleva un timestamp en microsegundos, que normalmente pone el coordinador o el cliente con la hora de su reloj. Cuando hay versiones distintas del mismo dato en réplicas diferentes, gana la de timestamp mayor: last-write-wins (LWW), a nivel de cada celda.

### 2.3 Qué orden garantiza y hasta dónde

No hay un orden global. Lo que hay es:

- Por celda: la versión con timestamp más grande gana.
- Por partición: una escritura a una misma partición es atómica y aislada, pero eso no es lo mismo que ordenarla respecto a otras escrituras concurrentes.
- Orden fuerte (opcional): para operaciones condicionales (`IF NOT EXISTS`) existen las lightweight transactions, que usan Paxos y dan comportamiento linealizable por partición, con más latencia.

Entre particiones o nodos distintos no se garantiza un orden causal.

### 2.4 Qué pasa cuando los relojes no coinciden

Aquí está el punto débil. Cassandra depende de que los relojes estén razonablemente sincronizados (NTP o Chrony). Si un cliente o nodo tiene el reloj adelantado, sus escrituras ganan aunque hayan ocurrido antes en la realidad, y una escritura legítima más reciente puede perderse en silencio.

```
Tiempo real:     10:00:00                10:00:10
                    |                       |
Cliente A (reloj +30 s):  saldo = 100       |
                          timestamp 10:00:30|
Cliente B (reloj correcto):         saldo = 250
                                    timestamp 10:00:10

Réplica compara timestamps: 10:00:30 > 10:00:10
Resultado: saldo = 100  (la escritura real más reciente, B, se pierde)
```

Lo que sí hace es desempatar cuando dos timestamps son iguales: un borrado (tombstone) gana sobre un dato vivo, y entre dos valores gana el mayor al compararlos como bytes. La documentación recomienda mantener NTP bien configurado en todos los nodos.

En resumen, Cassandra acepta el reloj de pared tal cual y paga el precio: un reloj desfasado puede dar resultados incorrectos sin avisar.

---

## 3. Google Cloud Spanner (TrueTime)

### 3.1 Qué es y para qué se usa

Spanner es una base de datos relacional distribuida globalmente. Ofrece SQL y transacciones ACID entre regiones y continentes, y escala horizontalmente. Se usa en sistemas donde se necesita consistencia fuerte y escala a la vez: dentro de Google para sus propios servicios y, como producto, en Google Cloud.

### 3.2 Qué tiempo usa para ordenar

Usa un reloj físico con incertidumbre explícita, llamado TrueTime. En lugar de devolver una sola hora, `TT.now()` devuelve un intervalo `[earliest, latest]` que garantiza contener la hora real. Para que el intervalo sea angosto, los servidores de Google usan relojes atómicos y receptores GPS; en el paper original la incertidumbre típica es de pocos milisegundos.

Cada transacción recibe un timestamp de commit basado en TrueTime. Se puede ver como un híbrido: es un reloj físico, pero con un protocolo que se comporta como un orden confiable.

### 3.3 Qué orden garantiza y hasta dónde

Garantiza consistencia externa (equivalente a strict serializability) en todo el sistema: si la transacción T1 termina de hacer commit antes de que empiece T2, entonces el timestamp de T1 es menor que el de T2, aunque ocurran en continentes distintos. Es lo más fuerte de las tres tecnologías.

Además permite lecturas de solo lectura sin bloqueos sobre una foto consistente de la base en un instante dado.

### 3.4 Qué pasa cuando los relojes no coinciden

Spanner no ignora el problema ni lo esconde: lo mide y lo espera. Después de elegir el timestamp de commit `s`, la transacción aplica commit wait: no libera los candados ni confirma el commit hasta que `TT.now().earliest` ya es mayor que `s`, o sea, hasta estar seguro de que ese instante ya pasó en todo el mundo.

```
TT.now() = [earliest ........ latest]
                               s = timestamp de commit
                               |<--- commit wait --->|
                                                     earliest > s  -> commit visible
```

Si la incertidumbre crece, el intervalo se ensancha y la espera es más larga: se paga con latencia, no con incorrección.

---

## 4. Alcance del orden que garantiza cada tecnología

| | Una celda / llave | Una partición | Todo el sistema |
|---|---|---|---|
| Kafka | Sí (misma llave) | Sí (offsets) | No |
| Cassandra | Sí (LWW por celda) | Solo con LWT | No |
| Spanner | Sí | Sí | Sí (consistencia externa) |

## 5. Conexión con lo visto en clase

Ninguna de las tres usa relojes de Lamport o vectoriales de forma pura, y todas resuelven el mismo problema: el reloj de pared no es confiable para ordenar.

- Kafka lo resuelve con un solo punto de ordenamiento por partición. El offset hace el trabajo que haría un contador lógico, y a cambio no hay orden entre particiones.
- Cassandra acepta el reloj de pared y paga el precio: un reloj desfasado da resultados incorrectos. Un reloj lógico o vectorial habría detectado la concurrencia en lugar de elegir un ganador por la hora.
- Spanner invierte en hardware para que el reloj físico sea casi confiable y, para el resto, espera.

La idea central de Lamport sigue vigente: lo que importa es la causalidad (qué evento pudo influir en cuál), y cada sistema decide cuánto está dispuesto a pagar para respetarla.

## Fuentes

1. Apache Kafka. Documentation (secciones Design y Producer Configs). https://kafka.apache.org/documentation/
2. Apache Cassandra. Documentation (arquitectura, niveles de consistencia, lightweight transactions). https://cassandra.apache.org/doc/latest/
3. Corbett, J. C. et al. (2012). Spanner: Google's Globally-Distributed Database. OSDI '12. https://research.google/pubs/spanner-googles-globally-distributed-database-2/
4. Google Cloud. Spanner: TrueTime and external consistency. https://cloud.google.com/spanner/docs/true-time-external-consistency
5. Lamport, L. (1978). Time, Clocks, and the Ordering of Events in a Distributed System. Communications of the ACM, 21(7).
6. Apuntes de la clase S10 (relojes físicos, NTP, Lamport y relojes vectoriales).

Nota: usé IA como apoyo para organizar y redactar; revisé el contenido contra las fuentes y puedo explicarlo en clase.
