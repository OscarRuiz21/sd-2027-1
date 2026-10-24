package mx.mexibanco.mensajeria;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lab S09: publica un evento en el exchange "transferencias" y ESPERA a que el broker lo confirme.
 * Tres cosas cuidan el mensaje:
 *  - el publisher confirm (publisher-confirm-type: correlated): el broker contesta ack cuando ya lo
 *    tiene, o nack si no pudo. La CorrelationData une esa respuesta, que llega despues y por otro
 *    hilo, con este mensaje;
 *  - mandatory: si ninguna cola lo recibe, el broker lo REGRESA en lugar de tirarlo en silencio;
 *  - la entrega persistente: se escribe a disco y sobrevive a un reinicio si la cola es durable.
 *
 * Lo que NO cuida: si el proceso muere antes de llamar a publicar(), o si el broker esta caido, el
 * evento no sale. Por eso, desde el reto, la saga ya no lo llama directo: lo usa el relay del outbox
 * (Outbox.publicarPendientes), que vuelve a intentar hasta que el broker confirma.
 */
@Component
public class PublicadorDeEventos {

	private final RabbitTemplate rabbit;
	private final long esperaConfirmacionMs;

	public PublicadorDeEventos(RabbitTemplate rabbit,
							   @Value("${mexibanco.mensajeria.espera-confirmacion-ms:5000}") long esperaConfirmacionMs) {
		this.rabbit = rabbit;
		this.esperaConfirmacionMs = esperaConfirmacionMs;
	}

	/** Regresa solo si el broker confirmo el evento y alguna cola lo recibio. Si no, lanza EventoNoPublicadoException. */
	public void publicar(EventoTransferencia evento) {
		String id = evento.eventoId().toString();
		CorrelationData correlacion = new CorrelationData(id);
		try {
			rabbit.convertAndSend(ConfiguracionMensajeria.EXCHANGE, evento.routingKey(), evento, mensaje -> {
				mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT); // a disco
				mensaje.getMessageProperties().setMessageId(id); // se ve en el panel sin abrir el JSON
				return mensaje;
			}, correlacion);
		} catch (RuntimeException sinConexion) {
			throw new EventoNoPublicadoException("No se pudo mandar el evento " + id + " al broker: " + sinConexion.getMessage(), sinConexion);
		}
		CorrelationData.Confirm confirmacion;
		try {
			confirmacion = correlacion.getFuture().get(esperaConfirmacionMs, TimeUnit.MILLISECONDS);
		} catch (TimeoutException | ExecutionException sinRespuesta) {
			throw new EventoNoPublicadoException("El broker no confirmo el evento " + id + ": " + sinRespuesta, sinRespuesta);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
			throw new EventoNoPublicadoException("Se interrumpio la espera del evento " + id, interrumpido);
		}
		if (!confirmacion.isAck()) {
			throw new EventoNoPublicadoException("El broker rechazo el evento " + id + " (nack): " + confirmacion.getReason());
		}
		ReturnedMessage regresado = correlacion.getReturned(); // lo regreso por mandatory
		if (regresado != null) {
			throw new EventoNoPublicadoException("Ninguna cola recibio el evento " + id + " con la routing key "
				+ regresado.getRoutingKey() + " (" + regresado.getReplyText() + ")");
		}
	}
}
