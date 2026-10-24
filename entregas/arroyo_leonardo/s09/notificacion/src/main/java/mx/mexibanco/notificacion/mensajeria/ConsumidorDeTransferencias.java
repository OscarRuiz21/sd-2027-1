package mx.mexibanco.notificacion.mensajeria;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        EventoTransferencia evento;
        try {
            evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
            validar(evento);
        } catch (IOException | IllegalArgumentException error) {
            log.warn("VENENO tag={} -> DLQ: {}", tag, error.getMessage());
            canal.basicNack(tag, false, false);
            return;
        }

        int nuevos;
        try {
            nuevos = notificaciones.avisarDeTransferencia(evento).size();
        } catch (RuntimeException error) {
            log.error("FALLO_PROCESAMIENTO evento={} -> requeue", evento.eventoId(), error);
            canal.basicNack(tag, false, true);
            return;
        }
        log.info("evento={} tipo={} redelivered={} avisosNuevos={}", evento.eventoId(),
                evento.tipo(), mensaje.getMessageProperties().getRedelivered(), nuevos);
        canal.basicAck(tag, false);
    }

    private static void validar(EventoTransferencia e) {
        if (e == null || e.eventoId() == null || e.transferenciaId() == null
                || e.transferenciaId() <= 0 || e.fecha() == null || e.tipo() == null
                || e.clabeOrigen() == null || !e.clabeOrigen().matches("[0-9]{18}")
                || e.clabeDestino() == null || !e.clabeDestino().matches("[0-9]{18}")
                || e.clabeOrigen().equals(e.clabeDestino()) || e.monto() == null
                || e.monto().signum() <= 0 || e.saldoOrigen() == null) {
            throw new IllegalArgumentException("Evento incompleto o inválido");
        }
        if (!EventoTransferencia.REALIZADA.equals(e.tipo())
                && !EventoTransferencia.REVERTIDA.equals(e.tipo())) {
            throw new IllegalArgumentException("Tipo desconocido: " + e.tipo());
        }
        if (EventoTransferencia.REALIZADA.equals(e.tipo()) && e.saldoDestino() == null) {
            throw new IllegalArgumentException("Realizada sin saldoDestino");
        }
    }
}