# Lecturas: borradores para revisar, no comentarios publicados

Preparados con asistencia de Codex el 3 de noviembre de 2026. No afirman lectura
personal terminada, participación en clase ni publicación en Discussions.
Revisar las fuentes, elegir preguntas que realmente se quieran discutir y
adaptar el texto antes de publicarlo. Las lecturas 2–9 ya pasaron su ventana;
confirmar regularización. La 10 vence el 7 de noviembre antes de las 07:00,
hora de Ciudad de México; tarde hasta el domingo 8.

Se revisaron las páginas públicas de Discussions 2, 25, 35, 40, 46, 49, 53, 54 y
58. No se encontró el usuario DerekMstn114 en el contenido visible. GitHub puede
paginar u ocultar comentarios: esto NO certifica que no exista una participación.
La Lectura 8 está en Discussion 53 aunque falta en el índice de lecturas de main.

## Lectura 2 · Fallas parciales · Discussion 2

1. Si una llamada remota vence por timeout, ¿qué información necesita conservar
   el cliente para distinguir un reintento seguro de una segunda operación?
2. ¿Qué parte de la interfaz de una compra debería exponer que el resultado es
   incierto, en vez de ocultar la red detrás de una llamada que parece local?

Hallazgo propuesto: una falla parcial permite que un servidor siga trabajando
cuando el cliente ya dejó de esperar. Por eso cambiar la sintaxis de una llamada
no elimina la necesidad de diseñar recuperación e idempotencia.

Fuente: [Waldo et al., A Note on Distributed Computing](https://github.com/papers-we-love/papers-we-love/blob/main/distributed_systems/a-note-on-distributed-computing.pdf).
Destino: [Discussion 2](https://github.com/OscarRuiz21/sd-2027-1/discussions/2).

## Lectura 3 · Twelve-Factor · Discussion 25

1. Si dos réplicas guardan sesiones en memoria, ¿qué observaría un cliente al
   cambiar de réplica y cómo lo resolvería un servicio persistente compartido?
2. ¿Cómo separar configuración de código sin que las variables sensibles
   terminen copiadas en logs o dentro de una imagen Docker?

Hallazgo propuesto: que el proceso sea reemplazable no significa que toda la
aplicación carezca de estado; los datos durables se trasladan a servicios de
respaldo. Escalar procesos no replica por sí mismo sus archivos locales.

Fuentes: [Config](https://12factor.net/config), [Processes](https://12factor.net/processes).
La lectura complementaria Borg/Omega/Kubernetes necesita repaso adicional; no se
presenta este borrador como un resumen de ambos textos.
Destino: [Discussion 25](https://github.com/OscarRuiz21/sd-2027-1/discussions/25).

## Lectura 4 · Raft · Discussion 35

1. ¿Por qué una mayoría de votos no basta si se permite elegir un candidato
   cuyo log no contiene las entradas necesarias para preservar lo confirmado?
2. En un grupo de cinco nodos partido 3–2, ¿qué operaciones puede seguir
   confirmando cada lado y qué sucede cuando vuelve la comunicación?

Hallazgo propuesto: mayoría y restricción sobre qué candidato puede ganar
trabajan juntas. La elección de líder no consiste únicamente en escoger al
servidor que responde más rápido.

Fuente: [Raft, §5](https://raft.github.io/raft.pdf).
Destino: [Discussion 35](https://github.com/OscarRuiz21/sd-2027-1/discussions/35).

## Lectura 5 · CAP · Discussion 40

1. ¿Qué invariantes de una tienda impedirían aceptar compras en ambos lados
   de una partición aunque sí se permitan lecturas de catálogo?
2. ¿Qué información habría que guardar durante una partición para reconciliar
   después las operaciones que sí se decidieron aceptar?

Hallazgo propuesto: la decisión entre consistencia y disponibilidad aparece
ante la partición y puede diferir por operación. Etiquetar toda la aplicación
como CP o AP no describe qué hace durante la recuperación.

Fuente: [Brewer, CAP Twelve Years Later](https://www.infoq.com/articles/cap-twelve-years-later-how-the-rules-have-changed/).
Destino: [Discussion 40](https://github.com/OscarRuiz21/sd-2027-1/discussions/40).

## Lectura 6 · Circuit breaker y saga · Discussion 46

1. Si el abono vence por timeout después de un cargo, ¿cuándo es correcto
   compensar y cuándo primero hay que consultar el resultado del abono?
2. Si el circuito está abierto, ¿cómo se conserva una compensación pendiente
   para que sobreviva al reinicio del servicio que la coordina?

Hallazgo propuesto: el circuito limita llamadas a un servicio con problemas;
no deshace efectos ya confirmados. La saga necesita operaciones compensadoras
y estado persistente además del mecanismo de protección.

Fuentes: [Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html),
[Saga](https://microservices.io/patterns/data/saga.html).
Destino: [Discussion 46](https://github.com/OscarRuiz21/sd-2027-1/discussions/46).

## Lectura 7 · Outbox · Discussion 49

1. ¿Cómo evita el Outbox que una transferencia confirmada quede sin evento si
   el proceso cae entre el commit y la publicación al broker?
2. Si el relevo publica y cae antes de marcar el evento como enviado, ¿qué llave
   debe usar el consumidor para que el segundo envío no repita el efecto?

Hallazgo propuesto: el Outbox guarda el cambio de negocio y la intención de
publicar en una transacción local. El relevo todavía puede duplicar mensajes;
por ello persiste la necesidad de un consumidor idempotente.

Fuente: [Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html).
El texto de Kreps requiere repaso complementario; no se afirma haberlo terminado.
Destino: [Discussion 49](https://github.com/OscarRuiz21/sd-2027-1/discussions/49).

## Lectura 8 · Lamport · Discussion 53

1. Si dos eventos tienen marcas 4 y 7, ¿qué evidencia adicional permite saber
   si están relacionados causalmente o son independientes?
2. ¿Qué gana una aplicación al desempatar por identificador de proceso y qué
   información sobre concurrencia sigue sin poder recuperar?

Hallazgo propuesto: a→b implica L(a)<L(b), pero la implicación inversa no vale.
Un orden total consistente con la causalidad puede ordenar también eventos que
no se causaron entre sí.

Fuente: [Lamport, Time, Clocks, and the Ordering of Events](https://lamport.azurewebsites.net/pubs/time-clocks.pdf).
Destino: [Discussion 53](https://github.com/OscarRuiz21/sd-2027-1/discussions/53).

## Lectura 9 · Persistencia políglota y Bitcoin · Discussion 54

1. ¿Qué costo de coordinación aparece al guardar pedidos y stock en motores
   distintos, aunque cada motor maneje bien su propio tipo de datos?
2. ¿Por qué una cadena con prueba de trabajo ofrece una forma de confianza
   diferente de la confirmación por mayoría en un grupo fijo de servidores?

Hallazgo propuesto: elegir un almacén según el acceso a los datos no elimina
las invariantes que cruzan almacenes. En Bitcoin el acuerdo depende de trabajo
acumulado y del supuesto sobre poder de cómputo honesto, no de contar cuentas.

Fuentes: [Polyglot Persistence](https://martinfowler.com/bliki/PolyglotPersistence.html),
[Bitcoin, §1–6](https://bitcoin.org/bitcoin.pdf).
Destino: [Discussion 54](https://github.com/OscarRuiz21/sd-2027-1/discussions/54).

## Lectura 10 · Observabilidad · Discussion 58

1. Si una transferencia tarda cuatro segundos, ¿cómo se propaga el contexto
   desde el gateway hasta cuenta y notificación para reconocer la misma solicitud
   en sus spans y logs, especialmente al cruzar RabbitMQ?
2. Si solo conservamos una muestra de las trazas, ¿qué combinación de métricas
   y logs permite detectar errores que no quedaron representados en esa muestra?

Hallazgo propuesto: un log aislado describe un evento, mientras una traza conecta
el recorrido de una operación entre servicios. La instrumentación y la
propagación de contexto permiten relacionar las señales; tener muchos logs por
separado no basta para localizar dónde se consumieron los cuatro segundos.

Fuente: [OpenTelemetry, Observability primer](https://opentelemetry.io/docs/concepts/observability-primer/).
Destino: [Discussion 58](https://github.com/OscarRuiz21/sd-2027-1/discussions/58).
