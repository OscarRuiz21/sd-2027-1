# S09 · Mensajería con RabbitMQ

Base docente: `05-mensajeria-base` de Mexi Banco. Esa rama ya incluye la saga
de referencia del profesor; no se atribuye esa saga a esta implementación.
Los cambios propios de esta entrega están en la publicación y el consumo de
eventos, idempotencia de avisos y manejo del mensaje inválido.

## Qué cambia

- Transferencia deja de llamar a notificación por HTTP y publica los eventos
  `TransferenciaRealizada` y `TransferenciaRevertida` del contrato oficial.
- Exchange topic durable `transferencias`, routing keys `transferencia.realizada`
  y `transferencia.revertida`, cola durable `notificacion.transferencias`.
- El ID se deriva de transferencia y tipo: volver a publicar el mismo hecho
  conserva la llave. La publicación usa mensajes persistentes y espera confirmación.
- El consumidor valida el contrato, guarda la llave única y sus avisos en una
  transacción y hace ack después del commit. Un replay no guarda avisos adicionales.
- Un JSON inválido, tipo desconocido o evento incompleto se rechaza sin reencolar.
  RabbitMQ lo mueve mediante `transferencias.dlx` a `notificacion.transferencias.dlq`.
- Una falla al guardar en base se reencola: no se trata como un mensaje venenoso.

`IDEMPOTENCIA_ACTIVA=false` y `DLQ_ACTIVA=false` son interruptores exclusivamente
para reproducir el antes del experimento. La versión normal los deja en `true`.
Si se cambia DLQ en una cola que ya existe, RabbitMQ rechaza la declaración:
vaciarla mediante consumo, verificar que esté vacía, eliminar solo esa cola y
reiniciar notificación. No borrar colas ni datos ajenos.

## Cómo reproducir

Clona la rama base y copia encima `codigo/transferencia` y `codigo/notificacion`.
Usa el compose de esta entrega. Construye un servicio a la vez por la caché Maven
compartida. El panel está en http://localhost:15672 (mexibanco / mexibanco).
Si hace falta, usa una réplica de cuenta y no arranques spei, como permite la guía.

Los Dockerfiles de los servicios modificados ejecutan `mvn verify`, incluidas las
pruebas. No se entregan directorios `target/`.

## Preguntas de cierre

**Broker caído.** Sin Outbox, el cambio a COMPLETADA puede quedar guardado y el
evento no salir. Se registra el error de publicación; no se devuelve dinero por
un aviso. La confirmación solo puede confirmar algo recibido por el broker: no
une la transacción PostgreSQL con RabbitMQ, ni reenvía el evento por sí sola.
Outbox es el reto opcional y no se presenta como implementado en esta entrega.

**Orden del ack.** Confirmar antes de guardar permite perder el aviso si el
consumidor cae después del ack. Confirmar después permite una redelivery cuando
el guardado sí ocurrió pero el ack se perdió. Se acepta esa repetición y la
restricción única del evento impide repetir los efectos en la base.

**Decisión y sacrificio.** Elijo avisos asíncronos para que un consumidor caído
no bloquee una transferencia. Lo hago para desacoplar sus tiempos de actividad.
Sacrifico saber en la respuesta HTTP que el aviso ya fue procesado: la bandeja
puede actualizarse después y necesito observar retrasos y fallas del consumidor.

Fuentes: [contrato oficial del lab](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s09-mensajeria/README.md),
[RabbitMQ: confirms y ack](https://www.rabbitmq.com/docs/confirms),
[dead letter exchanges](https://www.rabbitmq.com/docs/dlx).

## Ejecución conservada

El 20 de octubre se probaron las fases del enunciado sobre proyectos Docker aislados.
Los JSON conservan respuestas, cabeceras y horas; las capturas distinguen el panel
real de RabbitMQ de las vistas de esos registros. Idempotencia y DLQ quedaron
activadas al terminar; se retiró la falla de abono de demostración.

La compensación de la base docente respondió HTTP 502 con estado COMPENSADA,
saldos conservados y el aviso de devolución recibido. Ese código expresa que la
transferencia no se realizó, y no una caída del consumidor. No se implementó Outbox.

Incidencia local de arranque: el archivo interno de Erlang del broker tenía dueño
root y RabbitMQ no podía leerlo. Se corrigieron dueño y permiso 400 únicamente en
el volumen del contenedor de pruebas; después el broker quedó healthy.
