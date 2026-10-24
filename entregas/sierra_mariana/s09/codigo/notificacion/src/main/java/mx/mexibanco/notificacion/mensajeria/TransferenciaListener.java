package mx.mexibanco.notificacion;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TransferenciaListener {

    private final NotificacionService notificaciones;

    public TransferenciaListener(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    // Escuchamos la cola con el nombre exacto
    @RabbitListener(queues = "notificacion.transferencias")
    public void procesarTransferencia(EventoTransferencia evento, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            // Evaluamos el tipo de evento que llegó
            if ("TRANSFERENCIA_REALIZADA".equals(evento.tipo())) {
                // Dos avisos: uno a origen y otro a destino
                notificaciones.notificar(new AvisoDeMovimiento(
                        evento.claveOrigen(), "TRANSFERENCIA_ENVIADA", evento.monto().negate(), evento.saldoOrigen()));
                notificaciones.notificar(new AvisoDeMovimiento(
                        evento.claveDestino(), "TRANSFERENCIA_RECIBIDA", evento.monto(), evento.saldoDestino()));
                        
            } else if ("TRANSFERENCIA_REVERTIDA".equals(evento.tipo())) {
                // Un aviso: solo al origen informando la devolución
                notificaciones.notificar(new AvisoDeMovimiento(
                        evento.claveOrigen(), "DEVOLUCION_TRANSFERENCIA", evento.monto(), evento.saldoOrigen()));
            }
            
            // Ack manual después de guardar en la bandeja
            channel.basicAck(tag, false);

        } catch (Exception e) {
            channel.basicReject(tag, false);
        }
    }
}