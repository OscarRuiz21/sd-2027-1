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

        try {
            // Si el JSON viene roto, esto lanza una excepción inmediatamente
            EventoTransferencia evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
            
            List<Notificacion> avisos = notificaciones.avisarDeTransferencia(evento);
            log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
                evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

            // Procesado con éxito
            canal.basicAck(etiqueta, false);
            
        } catch (Exception e) {
            // Atrapa problemas de parseo de JSON o excepciones de negocio (ej. tipo desconocido)
            log.error("Mensaje envenenado. Enviando a DLQ. Motivo: {}", e.getMessage());
            
            // basicNack(deliveryTag, multiple, requeue)
            // requeue = false le dice al broker "no lo intentes más", y el broker lo manda a la DLQ
            canal.basicNack(etiqueta, false, false);
        }
    }
}