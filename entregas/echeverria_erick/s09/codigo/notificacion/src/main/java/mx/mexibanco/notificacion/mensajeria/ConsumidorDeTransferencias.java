package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/**
 * Consumidor de los eventos de transferencia. Tres desenlaces para cada mensaje:
 *  - se procesa bien              -> basicAck (despues de guardar);
 *  - es veneno (nunca se podra)   -> basicNack SIN reencolar: el broker lo manda a la DLQ;
 *  - fallo algo nuestro (la base) -> basicNack CON reencolar: el mensaje esta bien, se reintenta.
 */
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

        // 1. Leer el JSON. Si no se puede (cuerpo vacio, JSON roto), es veneno: reintentar no lo arregla.
        EventoTransferencia evento;
        try {
            evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
        } catch (JsonProcessingException ilegible) {
            log.error("[consumidor] mensaje ilegible, va a la DLQ: {}", ilegible.getOriginalMessage());
            canal.basicNack(etiqueta, false, false);   // requeue = false
            return;
        }

        // 2. JSON valido pero sin lo indispensable: tambien es veneno.
        if (evento == null || evento.eventoId() == null || evento.tipo() == null) {
            log.error("[consumidor] evento sin eventoId o sin tipo, va a la DLQ");
            canal.basicNack(etiqueta, false, false);
            return;
        }

        // 3. Guardar los avisos.
        List<Notificacion> avisos;
        try {
            avisos = notificaciones.avisarDeTransferencia(evento);   // aqui hace commit
        } catch (IllegalArgumentException tipoDesconocido) {
            // El service lanza esta excepcion con un tipo que no conoce: nunca se podra procesar.
            log.error("[consumidor] evento {} con tipo desconocido ({}), va a la DLQ",
                evento.eventoId(), evento.tipo());
            canal.basicNack(etiqueta, false, false);
            return;
        } catch (RuntimeException fallaNuestra) {
            // El mensaje esta bien; fallo la base u otra cosa nuestra. Se reencola para intentarlo de nuevo.
            log.error("[consumidor] evento {} no se pudo guardar, se reencola: {}",
                evento.eventoId(), fallaNuestra.getMessage());
            canal.basicNack(etiqueta, false, true);  
            return;
        }

        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

        canal.basicAck(etiqueta, false); 
    }
}