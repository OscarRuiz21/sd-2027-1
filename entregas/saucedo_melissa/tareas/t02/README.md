# T02 · Dos algoritmos de consenso que no son Raft

**Alejandra Melissa Saucedo Serralde· Sistemas Distribuidos 2027-1 · Grupo 2**

Elegí **Zab** (el protocolo de ZooKeeper) y **PBFT** (*Practical Byzantine Fault Tolerance*). Los escogí porque son muy distintos entre sí: Zab se parece a Raft porque tiene un líder y tolera servidores que **se caen**, mientras que PBFT tolera servidores que **mienten o se portan mal**. 

---

## 1. Zab (ZooKeeper Atomic Broadcast)

**Qué problema resuelve.** ZooKeeper replica su estado en varios servidores, y todos tienen que aplicar los cambios **en el mismo orden**. Zab es el protocolo de difusión atómica que hace eso. La documentación lo describe como "a leader based protocol similar to Paxos", pero a diferencia de Paxos recupera historias completas y no decisiones aisladas. El artículo original explica que un líder genera cambios que dependen del estado anterior, así que no se pueden reordenar: Zab está construido alrededor de ese **orden del líder** (*primary order*).

**Modelo de fallas.** Los procesos pueden **caerse y recuperarse**. No se asume que mientan. El progreso depende de que responda un **quórum** de seguidores. (En las fuentes que revisé no encontré el tamaño exacto del quórum, así que no lo afirmo.)

**Cómo funciona.** El tiempo se divide en **épocas** (*epochs*), y en cada época hay a lo más un líder que difunde. Cada cambio lleva un identificador `zxid`, formado por la época y un contador; se ordenan primero por época y luego por contador. Así, los cambios de un líder nuevo siempre quedan por encima de los de los líderes anteriores. Hay tres fases:

1. **Descubrimiento (establecer una época):** los seguidores le dicen al líder candidato su última época aceptada, y el líder propone una época **mayor** que todas. Los seguidores responden con su historia, y el líder escoge como historia inicial la del seguidor más actualizado. Con esto, los líderes anteriores ya no pueden confirmar nada nuevo.
2. **Sincronización:** el líder pone al día a cada seguidor: le manda un `DIFF` (lo que le falta), un `TRUNC` (para borrar lo que el líder no tiene) o un `SNAP` (una copia completa si está muy atrasado). Cuando un quórum confirma, el líder queda como líder de esa época.
3. **Difusión:** para cada cambio nuevo, el líder le asigna un `zxid`, manda un `PROPOSAL`, los seguidores lo guardan y responden `ACK`. Cuando un quórum lo confirmó, el líder manda `COMMIT`. Los seguidores aplican los cambios en orden.

**Si el líder falla.** Cualquier falla o tiempo de espera regresa a los servidores a la **elección de líder**. Si un seguidor recibe una propuesta de época vieja, cierra la conexión y vuelve a elegir. Un líder nuevo **no puede difundir nada hasta terminar la recuperación**: así no genera cambios sobre un estado desactualizado.

**Qué garantiza.** Según el artículo: integridad (solo se entrega lo que algún líder difundió), acuerdo, **orden total** (todos entregan en el mismo orden), orden local y global del líder (los cambios de un líder salen en el orden en que los mandó, y los de un líder anterior salen antes que los de uno posterior), y que un líder nuevo entrega todo lo que ya estaba entregado antes de difundir lo suyo.

## 2. PBFT (Practical Byzantine Fault Tolerance)

**Qué problema resuelve.** Poner de acuerdo a varias réplicas aunque algunas sean **bizantinas**: la presentación que revisé las define como cualquier nodo que no sigue el protocolo, ya sea por malicia, por un bug, por paquetes corruptos o porque suplanta a otro. Es el algoritmo de Castro y Liskov (SOSP '99).

**Modelo de fallas y cuántas réplicas.** Con `n = 3f + 1` réplicas se toleran hasta `f` réplicas con comportamiento arbitrario. (Un algoritmo que solo tolera caídas, como Zab, necesita `2f + 1`.) El supuesto importante es que las fallas son **independientes**: si varias réplicas fallan por la misma causa, se puede pasar de `f`.

**Cómo funciona en el caso normal.** Hay un **primario** y los demás son respaldos. Cada solicitud pasa por tres fases:

1. **Pre-prepare:** el primario le asigna un número de secuencia a la solicitud y la difunde con su resumen (*digest*).
2. **Prepare:** los respaldos difunden mensajes `PREPARE`. Una réplica queda "preparada" cuando tiene el `PRE-PREPARE` y `2f` mensajes `PREPARE` que coinciden. Con esto, dos réplicas honestas no pueden preparar solicitudes distintas con el mismo número de secuencia en la misma vista.
3. **Commit:** las réplicas difunden `COMMIT`. Al juntar `2f + 1`, ejecutan la operación y responden al cliente. Esta fase asegura que al menos `f + 1` réplicas honestas saben que la solicitud quedó preparada, de modo que la decisión sobrevive aunque el primario falle.

El **cliente** espera `f + 1` respuestas iguales: así al menos una viene de una réplica honesta.

**Si el primario falla o miente (cambio de vista).** Las "vistas" rotan el primario. Si un respaldo espera demasiado una solicitud pendiente, pide un cambio de vista mandando lo que ya tenía preparado. El primario nuevo manda `NEW-VIEW` cuando junta `2f` mensajes `VIEW-CHANGE`, vuelve a emitir lo que estaba preparado y rellena los huecos con operaciones vacías. Para que un solo nodo malicioso no provoque cambios de vista sin parar, el cambio solo arranca cuando `f + 1` réplicas lo piden por su cuenta.

**Autenticación.** Firmar cada mensaje con llave pública es caro, así que cada par de réplicas comparte una llave secreta y usa **MACs**. Para mensajes con varios destinatarios se usa un vector de MACs, uno por réplica. La presentación dice que generar un MAC es unas 100 veces más rápido que una firma RSA de 1024 bits.

**¿Qué garantiza y con qué supuesto?** La **seguridad** (*safety*: las réplicas honestas no se contradicen y lo ya confirmado sobrevive a los cambios de vista) no depende del tiempo; viene de que dos quórums de `2f + 1` entre `3f + 1` réplicas siempre comparten al menos una réplica honesta. La **vivacidad** (*liveness*: que el sistema avance) sí necesita un supuesto débil de sincronía: que el retraso de los mensajes no crezca más rápido que el tiempo mismo. Con ese supuesto PBFT esquiva el resultado de imposibilidad de consenso asíncrono.

## 3. Comparación

| | **Zab** | **PBFT** |
|---|---|---|
| Qué fallas tolera | Caídas y recuperaciones | Cualquier comportamiento, incluso mentir |
| Líder | Líder por época, elegido por elección | Primario por vista, rota por turnos |
| Réplicas necesarias | Un quórum de servidores | `3f + 1` para tolerar `f` |
| Fases por cambio | Propuesta, `ACK` de quórum, `COMMIT` | `PRE-PREPARE`, `PREPARE`, `COMMIT` |
| Cuando falla el líder | Nueva elección, nueva época, sincronizar historias | Cambio de vista con `2f` `VIEW-CHANGE` |
| Orden | `zxid` = época + contador | Número de secuencia dentro de la vista |
| Mensajes | Seguidores hablan con el líder | Todas las réplicas se hablan entre sí |

La diferencia de fondo es **en qué se confía**. Zab confía en que los servidores siguen el protocolo aunque se caigan, así que le basta con contar acuses de un quórum. PBFT no confía en ninguno, por eso necesita más réplicas, más fases y que todas se hablen entre sí.

---

## Fuentes

Zab
- [Zab 1.0, Apache ZooKeeper wiki (fases, épocas, zxid)](https://cwiki.apache.org/confluence/display/ZOOKEEPER/Zab1.0)
- [Zab: A simple totally ordered broadcast protocol, reporte técnico de Yahoo! (propiedades y orden del líder)](https://cwiki-test.apache.org/confluence/download/attachments/24193444/yl-2010-007.pdf?api=v2)

PBFT
- [Practical Byzantine Fault Tolerance, presentación de curso (cs6963, Utah): fallas bizantinas, 3f+1, tres fases, cambio de vista, MACs](https://users.cs.utah.edu/~stutsman/cs6963/public/pbft.pdf)
- [Notas sobre Castro y Liskov (supuesto de sincronía para la vivacidad)](https://adithyabhatkajake.github.io/notes/literature-notes/castroPracticalByzantineFault2002.html)


