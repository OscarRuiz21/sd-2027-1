/**
 * LAB S09: el consumidor de los eventos de transferencia. Hoy esta vacio a proposito: notificacion
 * todavia recibe los avisos por HTTP (POST /notificaciones, ver NotificacionController) y esta clase
 * no es un bean de Spring, asi que no hace nada.
 *
 * Lo que va aqui (el enunciado del lab tiene los nombres exactos):
 *  - declarar la cola notificacion.transferencias, enlazada al exchange transferencias con el
 *    patron transferencia.*, y su dead letter exchange y dead letter queue;
 *  - un metodo con @RabbitListener que lea cada evento, guarde los avisos y SOLO DESPUES haga ack;
 *  - que recibir dos veces el mismo evento no deje dos juegos de avisos;
 *  - que un mensaje que no se puede leer termine en la DLQ en vez de atorar la cola.
 */

package mx.mexibanco.notificacion.mensajeria;

import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionRepository;
import mx.mexibanco.notificacion.TransferenciaEvento;
import mx.mexibanco.notificacion.EventoProcesado;
import mx.mexibanco.notificacion.EventoProcesadoRepository;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConsumidorDeTransferencias {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);
    private final NotificacionRepository notificaciones;
    private final EventoProcesadoRepository eventos;

    public ConsumidorDeTransferencias(NotificacionRepository notificaciones, EventoProcesadoRepository eventos) {
        this.notificaciones = notificaciones;
        this.eventos = eventos;
    }

    @RabbitListener(queues = "notificacion.transferencias")
    @Transactional
    public void procesarEvento(TransferenciaEvento evento, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        try {
            //  Idempotencia
            if (eventos.existsById(evento.eventoId())) {
                channel.basicAck(tag, false);
                return;
            }

            // guarda avisos
            if ("TransferenciaRealizada".equals(evento.tipo())) {
                notificaciones.save(new Notificacion(evento.clabeOrigen(), "Transferencia enviada: Se enviaron $" + evento.monto()));
                notificaciones.save(new Notificacion(evento.clabeDestino(), "Transferencia recibida: Se recibieron $" + evento.monto()));
            } else if ("TransferenciaRevertida".equals(evento.tipo())) {
                notificaciones.save(new Notificacion(evento.clabeOrigen(), "Transferencia revertida: Tu transferencia falló, se devolvieron $" + evento.monto()));
            }

            // registra procesado
            eventos.save(new EventoProcesado(evento.eventoId()));

            // confirmar (ACK)
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("Error procesando mensaje. Se enviará a la DLQ. EventoID: {}", evento.eventoId(), e);
            //lo manda directo a la DLQ
            channel.basicNack(tag, false, false);
        }
    }
}