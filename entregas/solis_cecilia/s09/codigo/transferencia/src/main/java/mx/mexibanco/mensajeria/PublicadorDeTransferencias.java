package mx.mexibanco.mensajeria;

import mx.mexibanco.transferencia.Transferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PublicadorDeTransferencias {

    private static final Logger log =
            LoggerFactory.getLogger(PublicadorDeTransferencias.class);

    private final RabbitTemplate rabbit;

    public PublicadorDeTransferencias(RabbitTemplate rabbit) {
        this.rabbit = rabbit;

        rabbit.setConfirmCallback((correlacion, confirmado, causa) -> {
            String id = correlacion == null
                    ? "sin correlación"
                    : correlacion.getId();

            if (confirmado) {
                log.info("RabbitMQ CONFIRM ACK: {}", id);
            } else {
                log.error("RabbitMQ CONFIRM NACK: {}, causa: {}", id, causa);
            }
        });

        rabbit.setReturnsCallback(devuelto ->
                log.warn(
                        "RabbitMQ RETURN: eventoId={}, motivo={}, exchange={}, clave={}",
                        devuelto.getMessage()
                                .getMessageProperties().getMessageId(),
                        devuelto.getReplyText(),
                        devuelto.getExchange(),
                        devuelto.getRoutingKey()));
    }

    public void publicar(Transferencia transferencia) {
        String clave = switch (transferencia.getTipoEvento()) {
            case "TransferenciaRealizada" -> "transferencia.realizada";
            case "TransferenciaRevertida" -> "transferencia.revertida";
            default -> throw new IllegalArgumentException(
                    "Tipo de evento desconocido");
        };

        EventoTransferencia evento = new EventoTransferencia(
                transferencia.getEventoId(),
                transferencia.getTipoEvento(),
                transferencia.getId(),
                transferencia.getClaveOrigen(),
                transferencia.getClaveDestino(),
                transferencia.getMonto(),
                transferencia.getSaldoOrigenEvento(),
                transferencia.getSaldoDestinoEvento(),
                transferencia.getFechaEvento());

        // Cada intento tiene su correlación, pero el evento conserva su ID.
        CorrelationData correlacion = new CorrelationData(
                evento.eventoId() + ":" + UUID.randomUUID());

        try {
            rabbit.convertAndSend(
                    ConfiguracionRabbit.EXCHANGE,
                    clave,
                    evento,
                    mensaje -> {
                        mensaje.getMessageProperties()
                                .setMessageId(evento.eventoId());
                        mensaje.getMessageProperties()
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return mensaje;
                    },
                    correlacion);

            log.info(
                    "Evento enviado: eventoId={}, tipo={}, transferenciaId={}",
                    evento.eventoId(),
                    evento.tipo(),
                    evento.transferenciaId());

        } catch (AmqpException error) {
            // El dinero ya se procesó. No se revierte por fallar el aviso.
            // Sin Outbox, este evento NO se recupera automáticamente.
            log.error(
                    "No se pudo publicar eventoId={}. "
                            + "Sin recuperación automática en esta versión.",
                    evento.eventoId(),
                    error);
        }
    }
}