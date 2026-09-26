# T02 · Dos algoritmos de consenso

**Alumna:** Amy Veraza  
**Algoritmos elegidos:** PBFT y HotStuff

El consenso permite que varias réplicas acuerden un mismo orden de operaciones, incluso cuando
algunos nodos fallan o envían información contradictoria. Para esta tarea elegí dos protocolos
que toleran **fallas bizantinas**, es decir, nodos que pueden comportarse de manera arbitraria.

## 1. PBFT (Practical Byzantine Fault Tolerance)

PBFT replica una máquina de estados: todos los nodos correctos ejecutan las mismas solicitudes en
el mismo orden y, por lo tanto, llegan al mismo resultado. Para tolerar hasta `f` réplicas
bizantinas necesita al menos `3f + 1` réplicas. Por ejemplo, con cuatro réplicas puede tolerar que
una falle o mienta. Esto permite formar un cuórum de `2f + 1` votos que siempre contiene suficientes
nodos correctos para impedir que dos decisiones incompatibles sean aceptadas.

En una vista normal hay una réplica primaria y las demás funcionan como respaldo. El proceso para
ordenar una solicitud es el siguiente:

1. **Request:** el cliente envía una operación al nodo primario.
2. **Pre-prepare:** el primario asigna a la operación un número de secuencia y propone ese orden a
   los respaldos.
3. **Prepare:** cada réplica verifica la propuesta y la difunde a las demás. Cuando una réplica
   reúne suficientes mensajes compatibles, sabe que muchos nodos aceptaron la misma operación en
   la misma posición.
4. **Commit:** las réplicas vuelven a intercambiar votos. Al reunir `2f + 1` mensajes de commit,
   una réplica sabe que la decisión quedará preservada incluso si después cambia el primario.
5. **Reply:** la réplica ejecuta la operación y responde al cliente. El cliente acepta el resultado
   cuando recibe `f + 1` respuestas iguales, porque al menos una de ellas tuvo que venir de una
   réplica correcta.

Los mensajes se autentican para que un nodo no pueda hacerse pasar por otro. También se usan
*checkpoints* para descartar partes antiguas del registro sin perder un estado confirmado. Si el
primario deja de avanzar, las réplicas activan un **cambio de vista** y eligen otro. El nuevo
primario recopila evidencia de las operaciones ya preparadas para no sustituirlas por decisiones
contradictorias.

La ventaja principal de PBFT es que alcanza una decisión sin minería y ofrece finalidad: una
operación confirmada no queda pendiente de una cadena alternativa. Su costo aparece en la
comunicación. Durante prepare y commit cada réplica habla con las demás, por lo que el número de
mensajes crece aproximadamente de forma cuadrática. Esto funciona bien con grupos pequeños y
conocidos, pero dificulta escalar a cientos o miles de participantes.

## 2. HotStuff

HotStuff también replica una máquina de estados y tolera `f` nodos bizantinos usando por lo menos
`3f + 1` réplicas. Trabaja bajo **sincronía parcial**: la red puede tener retrasos impredecibles por
un tiempo, pero se supone que finalmente habrá un periodo en el que los mensajes lleguen dentro de
un límite. La seguridad se conserva durante los retrasos; el progreso se consigue cuando la red se
estabiliza y hay un líder correcto.

El protocolo avanza por vistas, cada una con un líder. Este propone un bloque que extiende una
propuesta anterior. Las réplicas validan la propuesta y envían su voto al líder en lugar de
difundirlo a todas las demás. Cuando el líder reúne `2f + 1` votos construye un **certificado de
cuórum** o QC. El QC es una prueba de que una supermayoría apoyó la propuesta y puede incluirse en
el siguiente mensaje.

En la presentación básica, una decisión atraviesa cuatro etapas: **prepare, pre-commit, commit y
decide**. En cada etapa, el líder reúne votos y produce un nuevo QC. Las réplicas mantienen una
propuesta bloqueada (*locked QC*) y solo votan por una rama que la extienda o que presente evidencia
más reciente. Esta regla evita que dos ramas incompatibles lleguen a decidirse. Las etapas pueden
encadenarse: mientras se propone un bloque nuevo, los certificados de bloques anteriores hacen
avanzar sus fases, formando una tubería de trabajo.

Si un líder falla, el componente llamado **pacemaker** hace avanzar la vista. Las réplicas informan
al líder nuevo sobre el QC más alto que conocen y este continúa desde la propuesta segura más
reciente. Así no es necesario que el cambio de líder reproduzca una gran cantidad de mensajes entre
todas las parejas de nodos.

La diferencia práctica frente a PBFT es que, en el camino normal, los votos van hacia el líder y
este distribuye un certificado compacto. Por ello la comunicación puede crecer linealmente con el
número de réplicas. Además, cuando la red ya es estable, un líder correcto avanza al ritmo del
retraso real de la red, propiedad llamada **responsividad**. El costo es que el protocolo necesita
varias fases y depende de un mecanismo de cambio de vista bien ajustado para recuperar el progreso
cuando el líder es lento o defectuoso.

## Comparación

| Aspecto | PBFT | HotStuff |
|---|---|---|
| Fallas toleradas | Hasta `f` con `3f + 1` réplicas | Hasta `f` con `3f + 1` réplicas |
| Organización | Primario y respaldos | Líder que cambia entre vistas |
| Evidencia de acuerdo | Mensajes prepare y commit | Certificados de cuórum de `2f + 1` votos |
| Comunicación normal | Aproximadamente cuadrática | Lineal con certificados agregados |
| Recuperación del líder | Protocolo de cambio de vista | Pacemaker y QC más alto conocido |
| Uso conveniente | Grupos pequeños de réplicas conocidas | Sistemas BFT que necesitan crecer y cambiar de líder con menor costo |

Ambos protocolos separan la **seguridad** del **progreso**: un periodo de red lenta puede detener
el avance, pero no debe permitir que dos operaciones contradictorias queden confirmadas. PBFT lo
consigue mediante dos rondas de intercambio entre réplicas; HotStuff resume los votos en
certificados que se encadenan. En ambos casos, la intersección entre cuórums garantiza que dos
decisiones válidas compartan nodos correctos y no puedan contradecirse.

## Fuentes

1. Miguel Castro y Barbara Liskov, “Practical Byzantine Fault Tolerance”, *OSDI 1999*.
   [Artículo (MIT)](https://pdos.csail.mit.edu/6.824/papers/castro-practicalbft.pdf) y
   [ficha de USENIX](https://www.usenix.org/conference/osdi-99/presentation/practical-byzantine-fault-tolerance).
2. Maofan Yin, Dahlia Malkhi, Michael K. Reiter, Guy Golan Gueta e Ittai Abraham,
   “HotStuff: BFT Consensus with Linearity and Responsiveness”, *PODC 2019*.
   [Artículo de los autores](https://reitermk.github.io/papers/2019/PODC.pdf),
   DOI: [10.1145/3293611.3331591](https://doi.org/10.1145/3293611.3331591).
