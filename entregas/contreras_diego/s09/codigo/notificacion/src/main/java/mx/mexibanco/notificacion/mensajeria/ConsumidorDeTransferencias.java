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
    private final long pausaAntesDelAckMs;

    public ConsumidorDeTransferencias(
            NotificacionService notificaciones,
            ObjectMapper json,
            @Value("${mexibanco.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs) {
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
        } catch (Exception e) {
            log.error("[consumidor] Mensaje envenenado (JSON no legible). Rechazando con basicNack sin reencolar hacia DLQ. Causa: {}", e.getMessage());
            // requeue = false para que RabbitMQ lo encamine hacia transferencias.dlx -> notificacion.transferencias.dlq
            canal.basicNack(etiqueta, false, false);
            return;
        }

        List<Notificacion> avisos = notificaciones.avisarDeTransferencia(evento);
        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

        if (pausaAntesDelAckMs > 0) {
            try {
                log.info("[consumidor] Pausando {} ms antes del ack para prueba de fallo en vuelo...", pausaAntesDelAckMs);
                Thread.sleep(pausaAntesDelAckMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        canal.basicAck(etiqueta, false);   // DESPUÉS de guardar: "ya lo tengo en BD, bórralo de la cola"
    }
}
