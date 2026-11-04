# T02 · Dos protocolos de consenso

Derek Santana Cardoso · Sistemas Distribuidos 2027-1 · 19 de octubre de 2026.
Preparado con asistencia de IA y fuentes primarias. Lectura personal y explicación
oral pendientes; no se afirma que este repaso ya se haya realizado.

## 1. Zab: ordenar cambios en ZooKeeper

Zab es el protocolo de difusión atómica de ZooKeeper. Su objetivo es que las
réplicas apliquen los cambios en el mismo orden. Tolera caídas; no supone que los
servidores puedan mentir arbitrariamente.

Primero se elige y activa un líder: sincroniza el historial con un quórum antes
de aceptar cambios nuevos. Cada propuesta lleva un `zxid`, formado por época y
contador. La época distingue mandatos; el contador ordena las operaciones.

El líder envía una propuesta; los seguidores la guardan y confirman. Cuando un
quórum la reconoce, el líder envía COMMIT. Así un cambio confirmado no depende
solo del disco del líder. Si este cae, la activación siguiente recupera un
historial compatible con lo ya confirmado.

Ejemplo: tres votantes necesitan dos. Si uno cae, los otros pueden progresar;
un servidor aislado no confirma cambios solo. Esto explica su utilidad para
coordinar configuración. Las lecturas locales pueden estar atrasadas: ordenar
escrituras no vuelve automáticamente linealizable cualquier lectura.

Fuente: [Apache ZooKeeper, Internals: Atomic Broadcast y Consistency Guarantees](https://zookeeper.apache.org/doc/current/zookeeperInternals.html).

## 2. PBFT: acuerdo incluso con réplicas que mienten

PBFT, de Castro y Liskov, replica una máquina de estados determinista y contempla
fallas bizantinas. Para tolerar hasta `f` réplicas defectuosas utiliza `3f + 1`.

Un primario propone un número de secuencia mediante PRE-PREPARE. Las réplicas
intercambian PREPARE para comprobar que coinciden en la operación y su posición;
después intercambian COMMIT. No basta creerle al primario. Con los certificados
necesarios, ejecutan las operaciones en orden. La condición de commit local
incluye `2f + 1` commits coincidentes; el cliente espera `f + 1` respuestas iguales.

Ejemplo: con cuatro réplicas se tolera una bizantina; dos respuestas coincidentes
permiten al cliente aceptar el resultado. Si el primario deja de trabajar,
un cambio de vista lo sustituye preservando las decisiones previas.

La seguridad no exige que todos los mensajes lleguen rápido, pero el progreso
necesita condiciones temporales favorables. Su costo es más comunicación y
réplicas que un protocolo pensado solo para caídas.

Fuente: [Castro y Liskov, Practical Byzantine Fault Tolerance, §3 y §4, OSDI 1999](https://www.usenix.org/legacy/publications/library/proceedings/osdi99/full_papers/castro/castro_html/node4.html).

## La diferencia que explicaría en clase

Zab sirve para un conjunto de servidores de confianza que pueden caer. PBFT
también considera comportamiento arbitrario. La elección depende del modelo de
falla que necesito tolerar, además del costo que puedo pagar.
