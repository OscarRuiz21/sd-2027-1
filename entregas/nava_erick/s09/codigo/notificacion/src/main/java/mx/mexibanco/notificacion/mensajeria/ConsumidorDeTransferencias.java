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
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

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

    @RabbitListener(queues = ConfiguracionMensajeria.COLA)
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

        EventoTransferencia evento;
        try {
            evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
        } catch (JsonProcessingException ilegible) {
            enviarALaDlq(canal, etiqueta, mensaje, "el JSON no se puede leer: " + ilegible.getOriginalMessage());
            return;
        }

        if (evento.eventoId() == null || evento.tipo() == null) {
            enviarALaDlq(canal, etiqueta, mensaje, "no trae eventoId o tipo");
            return;
        }

        List<Notificacion> avisos;
        try {
            avisos = notificaciones.avisarDeTransferencia(evento);  
        } catch (IllegalArgumentException tipoDesconocido) {
            enviarALaDlq(canal, etiqueta, mensaje, tipoDesconocido.getMessage());
            return;
        } catch (RuntimeException falloDeInfraestructura) {
            log.error("[consumidor] fallo al guardar el evento {} ({}): se reencola", evento.eventoId(),
                falloDeInfraestructura.getClass().getSimpleName(), falloDeInfraestructura);
            canal.basicNack(etiqueta, false, true);
            return;
        }
        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

        pausaDeDemo();                     
        canal.basicAck(etiqueta, false);  
    }

    /**
     * Mensaje envenenado: nack SIN reencolar (el último false es requeue). La cola principal tiene
     * configurado el dead letter exchange, así que es el BROKER quien lo mueve a la DLQ.
     */
    private void enviarALaDlq(Channel canal, long etiqueta, Message mensaje, String motivo) throws IOException {
        log.error("[consumidor] mensaje envenenado ({}). Se manda a la DLQ. Cuerpo: {}",
            motivo, recortar(new String(mensaje.getBody(), UTF_8)));
        canal.basicNack(etiqueta, false, false);
    }

    private static String recortar(String texto) {
        return texto.length() <= 200 ? texto : texto.substring(0, 200) + "...";
    }

    private void pausaDeDemo() {
        if (pausaAntesDelAckMs <= 0) {
            return;
        }
        log.info("[consumidor] pausa de {} ms antes del ack (demo)", pausaAntesDelAckMs);
        try {
            Thread.sleep(pausaAntesDelAckMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}