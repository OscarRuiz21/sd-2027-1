# T02 · Dos algoritmos de consenso que no son Raft

**Pablo Vaquero · Sistemas Distribuidos · 26 de septiembre de 2026**

Elegí **Zab** y **PBFT**. Ambos permiten que varias réplicas acuerden un orden
para procesar operaciones, pero consideran fallas diferentes. Una réplica es
un servidor que mantiene una copia del estado del servicio.

## 1. Zab — ZooKeeper Atomic Broadcast

Zab es el protocolo de difusión atómica de ZooKeeper: permite distribuir cambios
para que los servidores los apliquen en el mismo orden. Está pensado para caídas
y desconexiones, no para servidores que mienten deliberadamente. [1, 2]

### Cómo funciona

1. **Preparar al líder.** Se elige un líder y se establece una época, que identifica
   su periodo de liderazgo. Antes de aceptar cambios nuevos, se sincroniza el
   historial con un cuórum: el grupo de servidores necesario para respaldar una
   decisión. En la configuración habitual, es más de la mitad. [1, 2]
2. **Proponer el cambio.** El líder asigna a cada operación un identificador
   ordenado, llamado `zxid`, formado por la época y un contador, y envía la propuesta.
3. **Guardar y confirmar.** Los servidores guardan la propuesta en disco y envían
   una confirmación (`ACK`). Cuando la tiene una mayoría, contando al líder,
   este anuncia el `COMMIT`; las réplicas aplican los cambios en orden. [1]

Si cae el líder, se elige otro y se recupera un historial que conserva los cambios
confirmados. La sincronización puede completar registros faltantes o descartar
propuestas no confirmadas que quedaron fuera del historial elegido. [1, 2]

**Ejemplo:** con cinco servidores votantes se necesitan tres para confirmar un
cambio. Si la red se divide en grupos de tres y dos, solo el de tres puede
seguir confirmando, una vez que tenga un líder activo. Sin mayoría, las nuevas
escrituras se detienen. Este ejemplo aplica la regla de cuórum de [1].

## 2. PBFT — Practical Byzantine Fault Tolerance

PBFT contempla fallas bizantinas: un servidor puede mentir o contradecirse.
Para tolerar hasta `f` réplicas defectuosas utiliza `3f+1`
réplicas y mensajes autenticados. [3]

### Cómo funciona

1. **Pre-prepare.** El primario recibe la petición del cliente y propone su
   posición en la secuencia.
2. **Prepare.** Los respaldos intercambian mensajes para verificar la propuesta.
   Estar preparado requiere la petición, el `pre-prepare` y `2f` mensajes
   `prepare` coincidentes de respaldos distintos.
3. **Commit.** Las réplicas preparadas envían `commit`. Al reunir `2f+1` commits
   coincidentes, contando el propio, una réplica preparada ejecuta la operación
   después de las anteriores.
4. **Responder.** El cliente espera `f+1` respuestas iguales de réplicas distintas.
   Los tiempos de espera pueden activar un cambio de vista: reemplazar al
   primario conservando las decisiones confirmadas. [3]

**Ejemplo:** cuatro réplicas toleran una defectuosa; requieren tres commits y
dos respuestas iguales al cliente, en etapas distintas. [3]

Avanzar requiere que los retrasos de comunicación terminen permitiendo
completar el protocolo. [3]

## Comparación

| Aspecto | Zab | PBFT |
| --- | --- | --- |
| Fallas consideradas | Caídas y desconexiones | También comportamiento malicioso |
| Tolerar una falla | 3 servidores, con cuórum mayoritario | 4 réplicas |
| Idea principal | Ordenar cambios y recuperar el historial al cambiar de líder | Verificar la propuesta del primario entre réplicas |

## Fuentes

1. Apache Software Foundation. [ZooKeeper Internals: Atomic Broadcast y Quorums](https://zookeeper.apache.org/doc/current/zookeeperInternals).
2. Apache Software Foundation. [Zab 1.0: elección, épocas y sincronización del historial](https://cwiki.apache.org/confluence/spaces/ZOOKEEPER/pages/24189846/Zab1.0).
3. Miguel Castro y Barbara Liskov (1999). *Practical Byzantine Fault Tolerance*,
   OSDI. [Sección 3: propiedades y supuestos](https://www.usenix.org/legacy/publications/library/proceedings/osdi99/full_papers/castro/castro_html/node3.html)
   y [sección 4: algoritmo, fases y cambios de vista](https://www.usenix.org/legacy/publications/library/proceedings/osdi99/full_papers/castro/castro_html/node4.html).

Fuentes consultadas el 26 de septiembre de 2026. Los ejemplos numéricos son
ilustrativos; no representan pruebas ejecutadas.
