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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ConsumidorDeTransferencias {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final NotificacionService notificaciones;
    private final ObjectMapper json;
    private final long pausaAntesDelAckMs;

    public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json,
                                      @Value("${mexibanco.demo.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs) {
        this.notificaciones = notificaciones;
        this.json = json;
        this.pausaAntesDelAckMs = pausaAntesDelAckMs;
    }

    /**
     * Tres salidas posibles para cada mensaje:
     *   - ack: se guardaron los avisos (o ya estaban: idempotencia), el broker lo borra;
     *   - nack SIN reencolar: el mensaje es veneno (JSON ilegible, sin eventoId, tipo desconocido).
     *     Reintentarlo no lo arregla: el broker lo mueve a la DLQ para que lo revise una persona;
     *   - nack CON reencolar: el mensaje esta bien pero fallo algo nuestro (la base, por ejemplo).
     *     Ahi si conviene volver a intentarlo.
     */
    @RabbitListener(queues = ConfiguracionMensajeria.COLA)
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

        EventoTransferencia evento;
        List<Notificacion> avisos;
        try {
            evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
            avisos = notificaciones.avisarDeTransferencia(evento);   // aqui hace commit
        } catch (JsonProcessingException | IllegalArgumentException veneno) {
            log.error("[consumidor] mensaje invalido, va a la DLQ (nack sin reencolar): {} · cuerpo: {}",
                veneno.getMessage(), new String(mensaje.getBody(), StandardCharsets.UTF_8));
            canal.basicNack(etiqueta, false, false);   // requeue=false: el broker lo manda al DLX
            return;
        } catch (RuntimeException fallaNuestra) {
            log.error("[consumidor] no se pudo procesar por una falla interna; se reencola: {}",
                fallaNuestra.getMessage());
            canal.basicNack(etiqueta, false, true);    // requeue=true: el mensaje esta bien, reintentar
            return;
        }

        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

        pausaDeDemo(evento);   // los avisos YA estan en la base y el broker todavia no recibe el ack

        canal.basicAck(etiqueta, false);   // DESPUES de guardar: "ya lo tengo, borralo"
    }

    /**
     * Solo para la tarea del duplicado: abre una ventana entre el commit y el ack para matar el
     * consumidor a mano. Con PAUSA_ANTES_DEL_ACK_MS=0 (el valor normal) no hace nada.
     */
    private void pausaDeDemo(EventoTransferencia evento) {
        if (pausaAntesDelAckMs <= 0) {
            return;
        }
        log.warn("[consumidor] evento {} guardado; pausa de {} ms ANTES del ack (mata el servicio ahora)",
            evento.eventoId(), pausaAntesDelAckMs);
        try {
            Thread.sleep(pausaAntesDelAckMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
