# Programa de lecturas

Una lectura fundacional por sesión, asignada la semana anterior. Subes **2 preguntas +
1 hallazgo** en la [Discussion de la semana](https://github.com/OscarRuiz21/sd-2027-1/discussions):
**en tiempo hasta el sábado antes de las 07:00** (cuando abre la teoría) y **tarde hasta
el domingo todo el día** — tarde no penaliza, pero queda registrado para tu tendencia.
La teoría del sábado abre con las mejores preguntas (se votan con 👍).

## Lectura 1 · para la S2 (29 de agosto) · opcional

**Esta primera lectura es opcional y no se califica**: sirve para llegar con ventaja a la
discusión del sábado. El programa calificado arranca con la lectura de la S3.

- Lewis y Fowler, *Microservices* (2014): https://martinfowler.com/articles/microservices.html
- Fowler, *MonolithFirst*: https://martinfowler.com/bliki/MonolithFirst.html

## Lectura 2 · para la S3 (5 de septiembre) · **con esta arranca el programa calificado**

- Waldo, Wyant, Wollrath y Kendall, *A Note on Distributed Computing* (1994):
  https://github.com/papers-we-love/papers-we-love/blob/main/distributed_systems/a-note-on-distributed-computing.pdf
- 2 preguntas + 1 hallazgo en la [Discussion #2](https://github.com/OscarRuiz21/sd-2027-1/discussions/2)
  **antes del jueves 3 de septiembre, 23:59** (ese fue su plazo, con la política anterior; desde la Lectura 3 el corte es el sábado 06:59).

## Lectura 3 · para la S4 (12 de septiembre)

- The Twelve-Factor App (los 12 factores; atención a III Config y VI Procesos): https://12factor.net/
- Burns, Grant, Oppenheimer, Brewer y Wilkes, *Borg, Omega, and Kubernetes* (ACM Queue, 2016):
  https://queue.acm.org/detail.cfm?id=2898444
- 2 preguntas + 1 hallazgo en la [Discussion #25](https://github.com/OscarRuiz21/sd-2027-1/discussions/25):
  **en tiempo hasta el sábado 12-sep, 06:59 · tarde hasta el domingo 13-sep.**

El calendario completo de lecturas se publica aquí conforme avanza el curso.

## Lectura 4 · para la S5 (19 de septiembre)

- Ongaro y Ousterhout, *In Search of an Understandable Consensus Algorithm* (**Raft**, 2014),
  **secciones §1 a §5**: https://raft.github.io/raft.pdf
- Opcional, para verlo animado antes de leer: https://thesecretlivesofdata.com/raft/
- 2 preguntas + 1 hallazgo en la [Discussion #35](https://github.com/OscarRuiz21/sd-2027-1/discussions/35):
  **en tiempo hasta el sábado 19-sep, 06:59 · tarde hasta el domingo 20-sep.**

Pongan la atención en **elección de líder** (§5.1–§5.2), **replicación del log** (§5.3) y en
**por qué siempre es mayoría y no "todos"**. El paper menciona **FLP** y **Paxos**: no se
claven ahí, los vemos de panorama el sábado.

Para repasar después de la clase: *Understand RAFT without breaking your brain*,
https://youtu.be/IujMVjKvWP4 (un buen apoyo antes de la T02).

## Lectura 5 · para la S6 (26 de septiembre)

- Brewer, *CAP Twelve Years Later: How the "Rules" Have Changed* (IEEE Computer, 2012).
  Versión libre en InfoQ: https://www.infoq.com/articles/cap-twelve-years-later-how-the-rules-have-changed/
- 2 preguntas + 1 hallazgo en la [Discussion #40](https://github.com/OscarRuiz21/sd-2027-1/discussions/40):
  **en tiempo hasta el sábado 26-sep, 06:59 · tarde hasta el domingo 27-sep.**

Pongan la atención en **por qué "elige 2 de 3" es engañoso**, en la relación entre **partición
y timeout**, y en qué hace un sistema **durante la partición y al recuperarse**. Léanlo con la
mayoría de Raft en la cabeza: ¿qué sacrifica Raft cuando la red se parte?

## Lectura 6 · para la S8 (10 de octubre)

- Fowler, *CircuitBreaker*: https://martinfowler.com/bliki/CircuitBreaker.html
- Richardson, *Pattern: Saga*: https://microservices.io/patterns/data/saga.html
- Opcional: Garcia-Molina y Salem, *Sagas* (SIGMOD 1987), secciones 1 a 3:
  https://www.cs.cornell.edu/andru/cs711/2002fa/reading/sagas.pdf
- 2 preguntas + 1 hallazgo en la [Discussion #46](https://github.com/OscarRuiz21/sd-2027-1/discussions/46):
  **en tiempo hasta el sábado 10-oct, 06:59 · tarde hasta el domingo 11-oct.**

Léanlas con el caso de la tarea del lab de la S7 en la cabeza: la transferencia que contestó con
*timeout* aunque el dinero sí se movió. Pongan la atención en los **tres estados del circuit
breaker** y en la **compensación** de una saga: si el abono falla con el cargo ya aplicado,
¿reintentan, deshacen o esperan?
