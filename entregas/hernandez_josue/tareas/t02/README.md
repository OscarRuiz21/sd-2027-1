# T02 · Dos algoritmos de consenso

Josué Hernández Rubio · Sistemas Distribuidos · Grupo 2

## Viewstamped Replication (VR)

VR sirve para que varias copias de un servicio apliquen las operaciones en el mismo orden. Trabaja por *vistas*. En cada vista hay una réplica primaria que recibe las peticiones y les asigna un número para fijar su lugar en el registro.

La primaria manda la operación a las demás réplicas con un mensaje `PREPARE`. Las réplicas de respaldo la anotan en orden y responden con `PREPAREOK`. Para tolerar `f` caídas se usan `2f + 1` réplicas. Cuando la primaria recibe `f` respuestas de respaldo, ya tiene una mayoría contando su propia copia. Entonces ejecuta la operación en orden, responde al cliente e informa a los respaldos de lo que quedó confirmado.

Si la primaria deja de responder, las réplicas pasan a otra vista. La nueva primaria reúne registros de una mayoría para conservar las operaciones que ya se confirmaron y continuar en el mismo orden. Por ejemplo, con tres réplicas basta la primaria y un respaldo para confirmar una operación, y el sistema puede continuar si una réplica se cae y las otras dos se pueden comunicar.

## Tendermint

Tendermint se usa para acordar qué bloque se agrega en cada posición de una cadena. La votación se hace por rondas. En cada ronda, un validador propone un bloque y los demás lo revisan.

1. En `PREVOTE`, cada validador emite su primer voto según la validez del bloque y el bloqueo que pueda tener de una ronda anterior.
2. Si un validador reúne más de dos tercios del poder de voto en `PREVOTE` para ese bloque, lo bloquea y emite un `PRECOMMIT`. El bloqueo limita cuándo puede apoyar otro bloque en una ronda posterior.
3. El bloque se confirma cuando se obtiene más de dos tercios del poder de voto en `PRECOMMIT` para el mismo bloque y la misma ronda. Si la ronda termina sin decisión, empieza otra con otro proponente.

El protocolo está pensado para tolerar validadores que se comporten de forma arbitraria mientras controlen menos de un tercio del poder de voto. Por ejemplo, con cuatro validadores de igual peso se necesitan tres votos para superar el umbral de dos tercios.

## Fuentes

- Barbara Liskov y James Cowling (2012). [*Viewstamped Replication Revisited*](https://www.cs.princeton.edu/courses/archive/fall19/cos418/papers/vr-revisited.pdf).
- Ethan Buchman, Jae Kwon y Žarko Milosevic (2019). [*The latest gossip on BFT consensus*](https://arxiv.org/pdf/1807.04938).
