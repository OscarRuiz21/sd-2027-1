package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConsumidorDeTransferencias {

    private static final Logger log =
            LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final ObjectMapper json;
    private final NotificacionService notificaciones;

    public ConsumidorDeTransferencias(
            ObjectMapper json,
            NotificacionService notificaciones) {

        this.json = json;
        this.notificaciones = notificaciones;
    }

    @RabbitListener(queues = ConfiguracionRabbit.COLA)
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        EventoTransferencia evento;

        try {
            evento = json.readValue(
                    mensaje.getBody(),
                    EventoTransferencia.class);

        } catch (IOException | IllegalArgumentException error) {
            log.warn(
                    "Mensaje ilegible: se rechaza sin reencolar. bytes={}, causa={}",
                    mensaje.getBody().length,
                    error.getMessage());

            canal.basicNack(etiqueta, false, false);
            return;
        }

        try {
            // La transacción se confirma antes de retornar.
            notificaciones.recibirEvento(evento);

        } catch (IllegalArgumentException error) {
            log.warn(
                    "Evento inválido: se rechaza sin reencolar. causa={}",
                    error.getMessage());

            canal.basicNack(etiqueta, false, false);
            return;

        } catch (RuntimeException error) {
            log.error(
                    "Falló el guardado; se reencola. redelivered={}",
                    mensaje.getMessageProperties().isRedelivered(),
                    error);

            canal.basicNack(etiqueta, false, true);
            return;
        }

        canal.basicAck(etiqueta, false);

        log.info(
                "ACK después de guardar: eventoId={}, transferenciaId={}",
                evento.eventoId(),
                evento.transferenciaId());
    }
}