package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LAB S09, el lado del CONSUMIDOR. Los nombres son los del contrato del lab.
 *
 * <pre>
 *  transferencia --publica--> [transferencias] (topic) --transferencia.*--> notificacion.transferencias --> @RabbitListener
 *                                                                                  |
 *                                                    nack sin reencolar (veneno)   v
 *                                         [transferencias.dlx] (direct) --notificacion.transferencias--> notificacion.transferencias.dlq
 * </pre>
 *
 * Spring (RabbitAdmin) declara todo esto al conectarse. Declarar es idempotente si los argumentos
 * son los mismos; si la cola ya existia SIN los argumentos de dead letter, RabbitMQ responde
 * PRECONDITION_FAILED: hay que borrarla en el panel y reiniciar notificacion.
 *
 * El exchange "transferencias" tambien lo declara transferencia, con los mismos argumentos; asi no
 * importa cual de los dos servicios arranque primero.
 */
@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";
	public static final String COLA = "notificacion.transferencias";
	public static final String PATRON = "transferencia.*";
	public static final String DLX = "transferencias.dlx";
	public static final String DLQ = "notificacion.transferencias.dlq";
	/** La routing key con la que el DLX manda los mensajes muertos a la DLQ. */
	public static final String CLAVE_DLQ = "notificacion.transferencias";

	@Bean
	TopicExchange exchangeTransferencias() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	/** La cola principal: durable, y si un mensaje se rechaza sin reencolar, va al DLX con la clave de la DLQ. */
	@Bean
	Queue colaNotificaciones() {
		return QueueBuilder.durable(COLA)
			.deadLetterExchange(DLX)
			.deadLetterRoutingKey(CLAVE_DLQ)
			.build();
	}

	@Bean
	Binding enlaceNotificaciones(Queue colaNotificaciones, TopicExchange exchangeTransferencias) {
		return BindingBuilder.bind(colaNotificaciones).to(exchangeTransferencias).with(PATRON);
	}

	@Bean
	DirectExchange exchangeMuertos() {
		return new DirectExchange(DLX, true, false);
	}

	@Bean
	Queue colaMuertos() {
		return QueueBuilder.durable(DLQ).build();
	}

	@Bean
	Binding enlaceMuertos(Queue colaMuertos, DirectExchange exchangeMuertos) {
		return BindingBuilder.bind(colaMuertos).to(exchangeMuertos).with(CLAVE_DLQ);
	}
}
