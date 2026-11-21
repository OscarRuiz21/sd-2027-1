package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConsumidorDeTransferencias {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final NotificacionService servicio;
    private final ObjectMapper mapper;

    public ConsumidorDeTransferencias(NotificacionService servicio, ObjectMapper mapper) {
        this.servicio = servicio;
        this.mapper = mapper;
    }

    @RabbitListener(queues = RabbitConfig.COLA)
    public void alRecibir(Message mensaje, Channel canal,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        // 1. Leer el mensaje. Si no se puede leer o le faltan campos, nack SIN reencolar:
        //    la cola lo manda al DLX y termina en la DLQ, en vez de atorarse o perderse.
        TransferenciaEvento evento;
        try {
            evento = mapper.readValue(mensaje.getBody(), TransferenciaEvento.class);
            if (evento.eventoId() == null || evento.tipo() == null || evento.clabeOrigen() == null) {
                throw new IllegalArgumentException("evento incompleto");
            }
        } catch (Exception ilegible) {
            log.warn("[notificacion] mensaje ilegible ({}); nack sin reencolar, va a la DLQ", ilegible.getMessage());
            canal.basicNack(tag, false, false);
            return;
        }

        // 2. Guardar y SOLO DESPUES confirmar (idempotente por eventoId).
        boolean nuevo;
        try {
            nuevo = servicio.avisarTransferencia(evento);
        } catch (DataIntegrityViolationException duplicado) {
            nuevo = false;
        }
        canal.basicAck(tag, false);
        if (nuevo) {
            log.info("[notificacion] evento {} procesado y confirmado", evento.eventoId());
        } else {
            log.info("[notificacion] evento {} duplicado: ya estaba procesado, se confirma sin repetir avisos", evento.eventoId());
        }
    }
}