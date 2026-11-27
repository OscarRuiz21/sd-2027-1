package mx.mexibanco.transferencia.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LAB S09: publica un EventoTransferencia en el exchange "transferencias" y ESPERA la confirmacion
 * del broker (publisher confirm, spring.rabbitmq.publisher-confirm-type=correlated).
 *
 * Que garantiza la confirmacion (ack): que RabbitMQ RECIBIO el mensaje y se hizo cargo de el
 * (si hay cola durable y el mensaje es persistente, ya lo escribio a disco).
 * Que NO garantiza:
 *  - que haya una cola que lo reciba: sin cola el broker tambien confirma, y lo descarta. Por eso
 *    mandatory=true y se revisa el "returned" (ver ConfiguracionMensajeria);
 *  - que un consumidor lo procese: eso solo lo sabe el ack del consumidor, del otro lado;
 *  - nada si el broker esta caido: no hay a quien publicar ni quien confirme. Eso lo resuelve el
 *    outbox (SalidaDeEventos + RelayDeOutbox), no el confirm.
 */
@Component
public class PublicadorDeEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorDeEventos.class);

	private final RabbitTemplate plantilla;
	private final long esperaConfirmacionMs;

	public PublicadorDeEventos(RabbitTemplate plantilla,
							   @Value("${mexibanco.eventos.espera-confirmacion-ms:5000}") long esperaConfirmacionMs) {
		this.plantilla = plantilla;
		this.esperaConfirmacionMs = esperaConfirmacionMs;
	}

	/** Publica y espera el confirm. Lanza EventoNoPublicadoException si el broker no lo confirmo. */
	public void publicar(EventoTransferencia evento) {
		String routingKey = EventoTransferencia.routingKeyDe(evento.tipo());
		CorrelationData correlacion = new CorrelationData(evento.eventoId());
		try {
			plantilla.convertAndSend(ConfiguracionMensajeria.EXCHANGE, routingKey, evento, mensaje -> {
				mensaje.getMessageProperties().setMessageId(evento.eventoId());
				mensaje.getMessageProperties().setType(evento.tipo());
				mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
				return mensaje;
			}, correlacion);
		} catch (RuntimeException sinBroker) {
			throw new EventoNoPublicadoException("No se pudo publicar " + evento.tipo() + " " + evento.eventoId()
				+ ": " + sinBroker.getMessage(), sinBroker);
		}

		CorrelationData.Confirm confirmacion;
		try {
			confirmacion = correlacion.getFuture().get(esperaConfirmacionMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
			throw new EventoNoPublicadoException("Interrumpido esperando la confirmacion de " + evento.eventoId(), interrumpido);
		} catch (ExecutionException | TimeoutException sinConfirmacion) {
			throw new EventoNoPublicadoException("RabbitMQ no confirmo " + evento.eventoId() + " en "
				+ esperaConfirmacionMs + " ms", sinConfirmacion);
		}
		if (!confirmacion.isAck()) {
			throw new EventoNoPublicadoException("RabbitMQ rechazo (nack) " + evento.eventoId() + ": " + confirmacion.getReason(), null);
		}
		if (correlacion.getReturned() != null) {
			log.warn("Evento {} {} CONFIRMADO por RabbitMQ pero sin cola que lo reciba: se perdio", evento.tipo(), evento.eventoId());
		} else {
			log.info("Evento {} {} de la transferencia {} publicado ({}) y confirmado por RabbitMQ",
				evento.tipo(), evento.eventoId(), evento.transferenciaId(), routingKey);
		}
	}
}
