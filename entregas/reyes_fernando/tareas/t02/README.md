# T02 - Dos algoritmos de consenso

**Alumno:** Fernando Reyes Vázquez

Para esta tarea se eligieron **Viewstamped Replication (VR)** y **Tendermint**. Los dos permiten que varios nodos de un sistema distribuido lleguen a un acuerdo, pero lo hacen de formas diferentes y también están diseñados para enfrentar distintos tipos de fallas.

## 1. Viewstamped Replication (VR)

**Viewstamped Replication**, también conocido como VR, es un protocolo para mantener varias réplicas de un servicio con el mismo estado. Su idea principal es que, aunque alguno de los servidores falle, el sistema pueda continuar trabajando mientras todavía exista una mayoría de réplicas disponibles.

VR organiza el funcionamiento del sistema mediante **vistas (views)**. En cada vista existe una réplica que funciona como **primary**, mientras que las demás trabajan como **backups**. El primary es el encargado de recibir las solicitudes de los clientes y establecer el orden en que se van a realizar las operaciones.

Cuando un cliente manda una solicitud, el primary le asigna un número de operación y la agrega a su registro o log. Después envía un mensaje `PREPARE` a las réplicas de respaldo para informarles de la nueva operación. Los backups guardan la operación y responden con un mensaje `PREPARE-OK`.

Cuando el primary obtiene las respuestas necesarias de una mayoría de las réplicas, la operación se considera confirmada. El primary puede ejecutar la operación y responder al cliente. Los demás servidores también reciben información sobre las operaciones confirmadas para poder mantener una copia del estado del sistema lo más actualizada posible.

Una parte importante de VR es lo que ocurre cuando el primary falla. Los backups esperan recibir mensajes periódicamente del primary y, si durante cierto tiempo no reciben nada, pueden considerar que existe una falla y comenzar un **cambio de vista (view change)**.

Durante el cambio de vista, las réplicas avanzan a un nuevo número de vista y determinan cuál será el nuevo primary. Las réplicas también intercambian información sobre sus registros para que el nuevo primary pueda recuperar las operaciones que ya habían sido confirmadas y continuar desde un estado correcto.

Esto evita que una operación que ya había sido aceptada por una mayoría desaparezca solamente porque el primary anterior dejó de funcionar. Después de sincronizar el estado, se inicia la nueva vista y el sistema puede seguir procesando solicitudes.

Por ejemplo, si existen tres réplicas y una de ellas es el primary, las otras dos son backups. Si el primary falla, las dos réplicas restantes pueden realizar un cambio de vista y una de ellas pasa a ocupar el papel de primary.

En general, VR está pensado principalmente para manejar fallas donde un nodo deja de funcionar o deja de comunicarse correctamente, utilizando réplicas, mayorías y cambios de vista para mantener el servicio disponible.

## 2. Tendermint

**Tendermint** es un protocolo de consenso tolerante a fallas bizantinas o **BFT (Byzantine Fault Tolerant)**. Esto significa que no solamente considera el caso donde un nodo deja de funcionar, sino también situaciones donde un nodo puede enviar información incorrecta o comportarse de una forma diferente a la esperada.

Tendermint es conocido principalmente por utilizarse en sistemas de blockchain. En este caso los participantes encargados de llegar al consenso reciben el nombre de **validadores**.

El proceso se realiza mediante rondas. Para cada bloque que se quiere agregar existe un validador que funciona como **proposer** y propone un bloque a los demás. Después de recibir la propuesta, los validadores participan en dos etapas principales de votación llamadas **Prevote** y **Precommit**.

El funcionamiento simplificado de una ronda sería:

**Propose → Prevote → Precommit → Commit**

Primero, durante **Propose**, un validador propone cuál debería ser el siguiente bloque.

Después se realiza **Prevote**. Cada validador revisa la propuesta y envía su voto indicando si considera que ese bloque puede continuar en el proceso.

Si se obtiene más de dos tercios del poder de voto necesario para una propuesta, los validadores pueden continuar a la etapa de **Precommit**. En esta etapa vuelven a votar para confirmar que están de acuerdo con el bloque.

Finalmente, si más de dos tercios del poder de voto realizan un precommit para el mismo bloque, el bloque puede pasar a **Commit** y se considera confirmado.

No siempre se consigue llegar a un acuerdo durante la primera ronda. Por ejemplo, el proposer podría no responder, la propuesta podría ser inválida o los mensajes podrían tardar demasiado en llegar. En estos casos se puede iniciar una nueva ronda con otro proposer e intentar nuevamente el consenso.

Tendermint también utiliza un mecanismo de bloqueo. Cuando un validador llega a cierto nivel de acuerdo sobre un bloque puede quedar temporalmente bloqueado sobre esa propuesta. Esto ayuda a evitar que diferentes grupos de validadores terminen confirmando bloques distintos para la misma posición de la cadena.

El protocolo está diseñado para mantener sus propiedades de seguridad mientras menos de un tercio del poder de voto de los validadores sea bizantino. Por esta razón, para tomar decisiones importantes se busca obtener más de dos tercios del poder de voto.

Una diferencia importante respecto a protocolos basados en un coordinador estable es que Tendermint trabaja mediante rondas. En cada una existe un proposer encargado de presentar una propuesta, mientras que el resto de los validadores participa en la decisión mediante sus votos.

## Comparación entre Viewstamped Replication y Tendermint

Aunque ambos buscan que diferentes nodos lleguen a un mismo resultado, su funcionamiento tiene varias diferencias.

| Característica | Viewstamped Replication | Tendermint |
|---|---|---|
| Tipo de fallas | Principalmente fallas por caída de nodos | Puede tolerar fallas bizantinas |
| Coordinador | Un primary dentro de cada vista | Un proposer para cada ronda |
| Forma de llegar al acuerdo | El primary replica operaciones y espera una mayoría | Los validadores realizan Prevote y Precommit |
| Cantidad necesaria para avanzar | Mayoría de las réplicas | Más de 2/3 del poder de voto |
| Cambio del coordinador | Se realiza un cambio de vista | Se pasa a otra ronda con otro proposer |
| Uso principal | Replicación de servicios y máquinas de estado | Consenso BFT y blockchain |

Una de las principales diferencias es el tipo de falla que están preparados para soportar. En **Viewstamped Replication** normalmente se considera que un servidor puede dejar de funcionar o dejar de responder, pero no que intentará enviar información diferente intencionalmente a otros nodos.

En **Tendermint** sí se toma en cuenta que algunos validadores puedan tener un comportamiento incorrecto o bizantino. Por esta razón necesita un nivel de acuerdo mayor, utilizando más de dos tercios del poder de voto para confirmar un bloque.

También cambia la forma en la que se organizan los nodos. VR mantiene un **primary** durante una vista y solamente realiza un cambio de vista cuando es necesario reemplazarlo. Tendermint trabaja mediante **rondas**, en las que existe un proposer y después todos los validadores participan mediante las etapas de Prevote y Precommit.

Por lo tanto, aunque los dos buscan mantener un conjunto de nodos de acuerdo, Viewstamped Replication se enfoca más en mantener réplicas de un servicio ante fallas de servidores, mientras que Tendermint está diseñado para llegar a acuerdos incluso cuando algunos participantes pueden tener comportamientos bizantinos.

## Fuentes

- Cowling, J. y Liskov, B. (2012). *Viewstamped Replication Revisited*. MIT Computer Science and Artificial Intelligence Laboratory. https://dspace.mit.edu/entities/publication/80846d94-fcd3-40e6-87fb-8d91fe99a5d1

- Tendermint Core. *Consensus Specification*. Documentación de Tendermint. https://docs.tendermint.com/master/spec/consensus/consensus.html

- Tendermint Core. *What is Tendermint?*. Documentación de Tendermint. https://docs.tendermint.com/v0.33/introduction/what-is-tendermint.html

- Parra Hernández, A. de la. (2023). *Estudio práctico y didáctico de los algoritmos de consenso distribuido*. Universidad Politécnica de Madrid. Fuente en español. https://oa.upm.es/74960/
