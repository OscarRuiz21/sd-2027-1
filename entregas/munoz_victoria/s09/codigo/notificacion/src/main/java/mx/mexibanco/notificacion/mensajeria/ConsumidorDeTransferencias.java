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

    public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json,
                                      @Value("${mexibanco.mensajeria.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs) {
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
            log.error("[consumidor] mensaje ilegible; se envia a DLQ", ilegible);
            canal.basicNack(etiqueta, false, false);
            return;
        }
        List<Notificacion> avisos = notificaciones.avisarDeTransferencia(evento);
        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());
        pausaAntesDelAck();
        canal.basicAck(etiqueta, false);
    }

    private void pausaAntesDelAck() {
        if (pausaAntesDelAckMs <= 0) return;
        try {
            Thread.sleep(pausaAntesDelAckMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
