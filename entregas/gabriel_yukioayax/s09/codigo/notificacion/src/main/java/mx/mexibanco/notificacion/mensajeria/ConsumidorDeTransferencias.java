package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ConsumidorDeTransferencias {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final NotificacionService notificaciones;
    private final ObjectMapper json;

    /** Solo para el experimento del duplicado: espera entre guardar los avisos y hacer el ack. 0 = sin pausa. */
    private final long pausaAntesDelAckMs;

    public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json,
                                      @Value("${mexibanco.demo.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs) {
        this.notificaciones = notificaciones;
        this.json = json;
        this.pausaAntesDelAckMs = pausaAntesDelAckMs;
    }

    @RabbitListener(queues = ConfiguracionMensajeria.COLA)
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

        EventoTransferencia evento;
        try {
            evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
        } catch (IOException ilegible) {
            // Veneno: por mas que se reintente no se va a poder leer. Nack sin reencolar y el broker
            // lo manda a la DLQ en vez de atorar la cola.
            log.warn("[consumidor] mensaje ilegible (redelivered={}), va a la DLQ: {}", reentregado, ilegible.getMessage());
            canal.basicNack(etiqueta, false, false);   // el ultimo false es requeue
            return;
        }
        List<Notificacion> avisos = notificaciones.avisarDeTransferencia(evento);   // aqui hace commit

        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

        if (pausaAntesDelAckMs > 0) {
            // Los avisos ya estan guardados y el broker todavia no lo sabe: si me matan aqui, lo reentrega.
            log.info("[consumidor] avisos guardados, espero {} ms antes del ack", pausaAntesDelAckMs);
            try {
                Thread.sleep(pausaAntesDelAckMs);
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
        }

        canal.basicAck(etiqueta, false);   // DESPUES de guardar: "ya lo tengo, borralo"
    }
}   