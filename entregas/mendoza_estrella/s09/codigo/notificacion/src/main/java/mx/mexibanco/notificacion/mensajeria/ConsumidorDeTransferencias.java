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

    private static final Logger log =
            LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final ObjectMapper json;
    private final NotificacionService notificaciones;

    public ConsumidorDeTransferencias(
            ObjectMapper json,
            NotificacionService notificaciones
    ) {
        this.json = json;
        this.notificaciones = notificaciones;
    }

    @RabbitListener(queues = ConfiguracionMensajeria.COLA)
    public void recibir(Message mensaje, Channel canal) throws IOException {

        long tag = mensaje.getMessageProperties().getDeliveryTag();

        boolean reentregado = Boolean.TRUE.equals(
                mensaje.getMessageProperties().isRedelivered()
        );

        EventoTransferencia evento;

        // Leer y validar el mensaje
        try {
            evento = json.readValue(
                    mensaje.getBody(),
                    EventoTransferencia.class
            );

            if (evento == null
                    || evento.eventoId() == null
                    || evento.transferenciaId() == null
                    || evento.tipo() == null
                    || evento.clabeOrigen() == null
                    || evento.monto() == null
                    || evento.saldoOrigen() == null) {

                throw new IllegalArgumentException(
                        "El evento tiene campos obligatorios faltantes"
                );
            }

            if (!EventoTransferencia.REALIZADA.equals(evento.tipo())
                    && !EventoTransferencia.REVERTIDA.equals(evento.tipo())) {

                throw new IllegalArgumentException(
                        "Tipo de evento desconocido: " + evento.tipo()
                );
            }

            if (EventoTransferencia.REALIZADA.equals(evento.tipo())
                    && (evento.clabeDestino() == null
                    || evento.saldoDestino() == null)) {

                throw new IllegalArgumentException(
                        "TransferenciaRealizada incompleta"
                );
            }

        } catch (IOException | IllegalArgumentException error) {

            log.error(
                    "[consumidor] Mensaje invalido; se envia a DLQ: {}",
                    error.getMessage()
            );

            // Rechazar sin reencolar
            canal.basicReject(tag, false);
            return;
        }

        // Procesar el evento valido
        try {
            List<Notificacion> avisos =
                    notificaciones.avisarDeTransferencia(evento);

            log.info(
                    "[consumidor] {} de la transferencia {} " +
                    "(evento {}, redelivered={}): {} aviso(s) nuevo(s)",
                    evento.tipo(),
                    evento.transferenciaId(),
                    evento.eventoId(),
                    reentregado,
                    avisos.size()
            );

            // Confirmar despues de guardar correctamente
            canal.basicAck(tag, false);

        } catch (RuntimeException error) {

            log.error(
                    "[consumidor] Error procesando evento {}: {}",
                    evento.eventoId(),
                    error.getMessage()
            );

            // Ante un fallo de procesamiento, permitir reintento
            canal.basicNack(tag, false, true);
        }
    }
}
