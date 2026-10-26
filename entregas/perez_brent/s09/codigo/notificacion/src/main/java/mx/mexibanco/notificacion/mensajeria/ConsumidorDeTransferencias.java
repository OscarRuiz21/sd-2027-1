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

        long etiqueta =
            mensaje.getMessageProperties().getDeliveryTag();

        boolean reentregado =
            Boolean.TRUE.equals(
                mensaje.getMessageProperties().getRedelivered()
            );

        EventoTransferencia evento;

        // 1. Validar que el mensaje se pueda leer
        // y contenga los datos esenciales.
        try {
            evento = json.readValue(
                mensaje.getBody(),
                EventoTransferencia.class
            );

            if (evento.eventoId() == null
                    || evento.tipo() == null
                    || evento.transferenciaId() == null
                    || (!EventoTransferencia.REALIZADA.equals(evento.tipo())
                        && !EventoTransferencia.REVERTIDA.equals(evento.tipo()))) {
                throw new IllegalArgumentException(
                    "Evento incompleto o tipo desconocido"
                );
            }

        } catch (IOException | IllegalArgumentException veneno) {

            log.error(
                "[consumidor] Mensaje inválido; se envía a DLQ",
                veneno
            );

            canal.basicNack(etiqueta, false, false);
            return;
        }

        // 2. Procesar el evento y guardar las notificaciones.
        try {
            List<Notificacion> avisos =
                notificaciones.avisarDeTransferencia(evento);

            log.info(
                "[consumidor] {} de la transferencia {} "
                    + "(evento {}, redelivered={}): {} aviso(s) nuevo(s)",
                evento.tipo(),
                evento.transferenciaId(),
                evento.eventoId(),
                reentregado,
                avisos.size()
            );

            // 3. Confirmar solamente después de guardar.
            canal.basicAck(etiqueta, false);

        } catch (org.springframework.dao.DataAccessException falloBD) {

            log.error(
                "[consumidor] Error de base de datos; se reencola",
                falloBD
            );

            canal.basicNack(etiqueta, false, true);

        } catch (RuntimeException falloProcesamiento) {

            log.error(
                "[consumidor] Error no recuperable al procesar",
                falloProcesamiento
            );

            canal.basicNack(etiqueta, false, false);
        }
    }
}