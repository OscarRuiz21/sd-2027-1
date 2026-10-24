package mx.mexibanco.mensajeria;

import mx.mexibanco.transferencia.Transferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublicadorDeTransferencias {
    private static final Logger log = LoggerFactory.getLogger(PublicadorDeTransferencias.class);
    private final RabbitTemplate rabbit;

    public PublicadorDeTransferencias(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /** Se invoca DESPUES de guardar el estado final y los datos estables del evento. */
    public void publicar(Transferencia t) {
        String tipo;
        String routingKey;
        switch (t.getEstado()) {
            case COMPLETADA -> {
                tipo = "TransferenciaRealizada";
                routingKey = "transferencia.realizada";
            }
            case COMPENSADA -> {
                tipo = "TransferenciaRevertida";
                routingKey = "transferencia.revertida";
            }
            default -> throw new IllegalArgumentException("La saga todavia no tiene un evento final");
        }
        if (t.getEventoId() == null) {
            throw new IllegalStateException("Falta preparar y guardar el evento antes de publicarlo");
        }
        EventoTransferencia evento = new EventoTransferencia(t.getEventoId(), tipo, t.getId(),
            t.getClaveOrigen(), t.getClaveDestino(), t.getMonto(),
            t.getSaldoOrigenEvento(), t.getSaldoDestinoEvento(), t.getActualizada());

        rabbit.convertAndSend(RabbitConfig.EXCHANGE, routingKey, evento, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(evento.eventoId());
            return message;
        }, new CorrelationData(evento.eventoId()));
        log.info("Evento enviado tipo={} transferenciaId={} eventoId={} routingKey={}",
            tipo, t.getId(), evento.eventoId(), routingKey);
        // Este envio NO es un outbox: si falla la publicacion, no hay reenvio automatico.
    }
}
