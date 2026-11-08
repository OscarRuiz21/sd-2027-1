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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ConsumidorDeTransferencias {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final NotificacionService notificaciones;
    private final ObjectMapper json;

    public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json) {
        this.notificaciones = notificaciones;
        this.json = json;
    }

    @RabbitListener(queues = ConfiguracionMensajeria.COLA)
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

        // Veneno 1: no es un JSON legible. Reintentarlo no lo arregla: a la DLQ, sin reencolar.
        EventoTransferencia evento;
        try {
            evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
        } catch (IOException ilegible) {
            log.error("[consumidor] mensaje ilegible, se manda a la DLQ: {}", ilegible.getMessage());
            canal.basicNack(etiqueta, false, false);
            return;
        }
        // Veneno 2: JSON valido, pero sin eventoId o sin tipo. Tampoco se puede procesar nunca.
        if (evento.eventoId() == null || evento.tipo() == null) {
            log.error("[consumidor] evento sin eventoId o sin tipo, se manda a la DLQ");
            canal.basicNack(etiqueta, false, false);
            return;
        }

        List<Notificacion> avisos;
        try {
            avisos = notificaciones.avisarDeTransferencia(evento);   // aqui hace commit
        } catch (IllegalArgumentException tipoDesconocido) {
            // Veneno 3: el tipo no existe. A la DLQ. (Una falla de la base NO cae aqui: esa si podria arreglarse.)
            log.error("[consumidor] evento {} con tipo desconocido, se manda a la DLQ: {}", evento.eventoId(), tipoDesconocido.getMessage());
            canal.basicNack(etiqueta, false, false);
            return;
        }
        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());
        canal.basicAck(etiqueta, false);   // DESPUES de guardar: "ya lo tengo, borralo"
    }
}
