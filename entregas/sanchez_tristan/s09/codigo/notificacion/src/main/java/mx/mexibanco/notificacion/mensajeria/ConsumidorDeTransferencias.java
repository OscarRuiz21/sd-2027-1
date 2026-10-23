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
            if (evento == null || evento.eventoId() == null || evento.tipo() == null) {
                throw new IllegalArgumentException("El evento no contiene eventoId o tipo");
            }
            if (!EventoTransferencia.REALIZADA.equals(evento.tipo()) && !EventoTransferencia.REVERTIDA.equals(evento.tipo())) {
                throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
            }
        } catch (Exception veneno) {
            log.error("[consumidor] Mensaje envenenado (etiqueta {}): {}. Rechazando sin reencolar hacia la DLQ",
                etiqueta, veneno.getMessage());
            canal.basicNack(etiqueta, false, false);   // requeue = false -> el broker lo manda a la DLQ
            return;
        }

        List<Notificacion> avisos;
        try {
            avisos = notificaciones.avisarDeTransferencia(evento);   // aquí hace commit
        } catch (Exception falloBase) {
            log.error("[consumidor] Fallo transitorio al procesar evento {} (etiqueta {}): {}. Reencolando",
                evento.eventoId(), etiqueta, falloBase.getMessage());
            canal.basicNack(etiqueta, false, true);    // requeue = true -> reintento
            return;
        }

        log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

        pausar(pausaAntesDelAckMs);

        canal.basicAck(etiqueta, false);   // DESPUÉS de guardar: "ya lo tengo, bórralo"
    }

    private void pausar(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}