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

@Component
public class ConsumidorDeTransferencias {

    private static final Logger log =
            LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final NotificacionService notificaciones;
    private final ObjectMapper json;

    public ConsumidorDeTransferencias(
            NotificacionService notificaciones,
            ObjectMapper json) {

        this.notificaciones = notificaciones;
        this.json = json;
    }

    @RabbitListener(queues = ConfiguracionMensajeria.COLA)
    public void recibir(Message mensaje, Channel canal) throws IOException {

        long etiqueta =
                mensaje.getMessageProperties().getDeliveryTag();

        EventoTransferencia evento;

        // 1. Validar que el mensaje sea JSON interpretable.
        try {
            evento = json.readValue(
                    mensaje.getBody(),
                    EventoTransferencia.class
            );

            if (evento == null || evento.eventoId() == null) {
                throw new IllegalArgumentException(
                        "El evento no contiene eventoId");
            }

            if (!EventoTransferencia.REALIZADA.equals(evento.tipo())
                    && !EventoTransferencia.REVERTIDA.equals(evento.tipo())) {
                throw new IllegalArgumentException(
                        "Tipo de evento desconocido: " + evento.tipo());
            }

        } catch (JsonProcessingException | IllegalArgumentException error) {

            log.error(
                    "[DLQ] Mensaje invalido; se rechaza sin reencolar: {}",
                    error.getMessage()
            );

            // Rechazar definitivamente: RabbitMQ lo envia a la DLQ.
            canal.basicNack(etiqueta, false, false);
            return;
        }

        // 2. Guardar los avisos en PostgreSQL.
        // La idempotencia se maneja dentro de este metodo.
        List<Notificacion> avisos =
                notificaciones.avisarDeTransferencia(evento);

        log.info(
                "[consumidor] {} de transferencia {} (evento {}): {} aviso(s) nuevo(s)",
                evento.tipo(),
                evento.transferenciaId(),
                evento.eventoId(),
                avisos.size()
        );

        // 3. Confirmar solamente despues de guardar correctamente.
        canal.basicAck(etiqueta, false);
    }
}
