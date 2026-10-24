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
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

        try {
            EventoTransferencia evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
            List<Notificacion> avisos = notificaciones.avisarDeTransferencia(evento);   
            log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
                evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

            canal.basicAck(etiqueta, false);
        } catch (Exception e) {
            log.error("[consumidor] Mensaje envenenado, mandando a DLQ: {}", e.getMessage());
            // NUEVO S09: basicNack(etiqueta, multiple, requeue) -> requeue en false lo manda a la DLQ
            canal.basicNack(etiqueta, false, false); 
        }
    }

	// LAB S09: tu consumidor va aqui.
}
