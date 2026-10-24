package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lab S09: la cola es de notificacion. Se llama como su dueno y vive en el broker aunque notificacion
 * este apagado: ahi esperan los eventos. Al conectarse, Spring declara todo lo que esta aqui.
 *
 * Parte 4: la cola principal trae dos argumentos que le dicen al BROKER a donde mandar un mensaje que
 * el consumidor rechaza sin reencolar (basicNack con requeue=false): al exchange transferencias.dlx
 * con la clave notificacion.transferencias, que lo deja en notificacion.transferencias.dlq. Es el
 * broker quien lo mueve, no este servicio.
 *
 * Ojo: RabbitMQ no deja cambiar los argumentos de una cola que ya existe (PRECONDITION_FAILED). La
 * cola de la Parte 2 se creo sin ellos, asi que hubo que borrarla desde el panel antes de arrancar esta.
 */
@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";
	public static final String COLA = "notificacion.transferencias";
	public static final String PATRON = "transferencia.*";
	public static final String DLX = "transferencias.dlx";
	public static final String DLQ = "notificacion.transferencias.dlq";
	/** La clave con la que la cola principal manda sus rechazados al DLX (y con la que esta enlazada la DLQ). */
	public static final String CLAVE_DLQ = COLA;

	@Bean
	Declarables topologia() {
		// El mismo exchange que declara transferencia, con las mismas propiedades (topic, durable). Se
		// declara aqui tambien para poder enlazar la cola aunque notificacion arranque primero.
		TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
		// Durable: la cola y sus mensajes persistentes sobreviven a un reinicio del broker.
		Queue cola = QueueBuilder.durable(COLA)
			.deadLetterExchange(DLX)           // x-dead-letter-exchange
			.deadLetterRoutingKey(CLAVE_DLQ)   // x-dead-letter-routing-key
			.build();
		// El DLX es direct: solo hay una clave y una cola detras. La DLQ es durable y nadie la consume:
		// ahi se quedan los mensajes envenenados hasta que una persona los revise.
		DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
		Queue dlq = QueueBuilder.durable(DLQ).build();
		return new Declarables(transferencias, cola, BindingBuilder.bind(cola).to(transferencias).with(PATRON),
			dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with(CLAVE_DLQ));
	}
}
