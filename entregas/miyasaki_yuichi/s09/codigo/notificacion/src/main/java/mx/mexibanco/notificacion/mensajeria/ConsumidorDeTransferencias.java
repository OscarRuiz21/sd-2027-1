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
        List<Notificacion> avisos;
        try {
            evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
            avisos = notificaciones.avisarDeTransferencia(evento);   // aquí hace commit
        } catch (IOException | IllegalArgumentException veneno) {
            // JSON ilegible, o un evento al que le faltan campos o con un tipo que no existe: por más que se
            // reintente nunca se va a poder procesar. Nack SIN reencolar: el broker lo manda a la DLQ.
            log.error("[consumidor] mensaje imposible de procesar (redelivered={}), va a la DLQ: {}",
                reentregado, veneno.getMessage());
            canal.basicNack(etiqueta, false, false);
            return;
        } catch (RuntimeException fallaNuestra) {
            // El mensaje está bien y lo que falló fue de este lado (la base, por ejemplo): reencolar para
            // que se intente otra vez. El rollback ya se llevó la marca y los avisos juntos.
            log.warn("[consumidor] no se pudo guardar el aviso (redelivered={}), se reencola: {}",
                reentregado, fallaNuestra.getMessage());
            canal.basicNack(etiqueta, false, true);
            return;
        }
        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

        pausaAntesDelAck();
        canal.basicAck(etiqueta, false);   // DESPUÉS de guardar: "ya lo tengo, bórralo"
    }

    /**
     * Solo para la tarea 5.1: abre una ventana entre el commit y el ack para matar notificacion en medio
     * (docker compose kill). Con PAUSA_ANTES_DEL_ACK_MS=0, el valor normal, no hace nada.
     */
    private void pausaAntesDelAck() {
        if (pausaAntesDelAckMs <= 0) {
            return;
        }
        log.info("[consumidor] avisos guardados; espero {} ms antes del ack", pausaAntesDelAckMs);
        try {
            Thread.sleep(pausaAntesDelAckMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
