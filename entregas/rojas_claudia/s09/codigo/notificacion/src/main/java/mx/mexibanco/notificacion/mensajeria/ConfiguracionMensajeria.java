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
 * S09: la cola es de notificacion (se llama como su duenio) y vive aunque notificacion este apagado.
 * Ahi esperan los eventos.
 *
 * Tarea del mensaje envenenado: la cola principal dice a donde se va un mensaje RECHAZADO sin
 * reencolar (x-dead-letter-exchange y x-dead-letter-routing-key). Es el BROKER quien lo mueve:
 *   notificacion.transferencias --nack(requeue=false)--> transferencias.dlx (direct)
 *     --notificacion.transferencias--> notificacion.transferencias.dlq
 *
 * OJO: RabbitMQ no deja cambiar los argumentos de una cola que ya existe (PRECONDITION_FAILED). Si la
 * cola de la seccion 4 ya existe sin DLX, hay que borrarla desde el panel (vacia) antes de arrancar.
 */
@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";
	public static final String COLA = "notificacion.transferencias";
	public static final String PATRON = "transferencia.*";
	public static final String DLX = "transferencias.dlx";
	public static final String DLQ = "notificacion.transferencias.dlq";
	/** La clave con la que el DLX manda los rechazados a la DLQ: el nombre de la cola de origen. */
	public static final String CLAVE_DLQ = COLA;

	@Bean
	Declarables topologia() {
		// El mismo exchange que declara transferencia, con las mismas propiedades (topic, durable).
		// Se declara aqui tambien para poder enlazar la cola aunque notificacion arranque primero.
		TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
		// Durable: la cola y sus mensajes persistentes sobreviven a un reinicio del broker.
		Queue cola = QueueBuilder.durable(COLA)
			.deadLetterExchange(DLX)             // x-dead-letter-exchange
			.deadLetterRoutingKey(CLAVE_DLQ)     // x-dead-letter-routing-key
			.build();
		// El dead letter exchange (direct) y la cola donde una persona revisa los mensajes rechazados.
		DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
		Queue dlq = QueueBuilder.durable(DLQ).build();
		return new Declarables(
			transferencias, cola, BindingBuilder.bind(cola).to(transferencias).with(PATRON),
			dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with(CLAVE_DLQ));
	}
}
