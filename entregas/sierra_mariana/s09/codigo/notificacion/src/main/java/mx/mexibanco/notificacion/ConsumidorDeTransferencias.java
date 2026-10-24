package mx.mexibanco.notificacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
public class ConsumidorDeTransferencias {

    private final NotificacionService notificaciones;
    private final EventoProcesadoRepository eventosRepo;
    private final ObjectMapper objectMapper;

    public ConsumidorDeTransferencias(NotificacionService notificaciones, EventoProcesadoRepository eventosRepo) {
        this.notificaciones = notificaciones;
        this.eventosRepo = eventosRepo;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    @RabbitListener(queues = "notificaciones.transferencias")
    public void procesarTransferencia(Message mensajeRaw, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            // Intentamos leer el JSON. Si está roto, lanzará una excepción y saltará al catch.
            EventoTransferencia evento = objectMapper.readValue(mensajeRaw.getBody(), EventoTransferencia.class);
            String id = evento.eventoId().toString();
            
            if (eventosRepo.existsById(id)) {
                channel.basicAck(tag, false);
                return;
            }

            eventosRepo.save(new EventoProcesado(id));

            if ("TRANSFERENCIA_REALIZADA".equals(evento.tipo())) {
                notificaciones.notificar(new AvisoDeMovimiento(
                        evento.claveOrigen(), "TRANSFERENCIA_ENVIADA", evento.monto().negate(), evento.saldoOrigen()));
                notificaciones.notificar(new AvisoDeMovimiento(
                        evento.claveDestino(), "TRANSFERENCIA_RECIBIDA", evento.monto(), evento.saldoDestino()));
            } else if ("TRANSFERENCIA_REVERTIDA".equals(evento.tipo())) {
                notificaciones.notificar(new AvisoDeMovimiento(
                        evento.claveOrigen(), "DEVOLUCION_TRANSFERENCIA", evento.monto(), evento.saldoOrigen()));
            }
            
            channel.basicAck(tag, false);

        } catch (Exception e) {
            // El mensaje está roto. Rechazamos SIN reencolar (false), así RabbitMQ lo manda a la DLQ.
            channel.basicReject(tag, false);
        }
    }
}