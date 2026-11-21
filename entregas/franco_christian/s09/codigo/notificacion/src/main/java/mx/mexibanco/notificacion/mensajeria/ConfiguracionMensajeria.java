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

@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";
	public static final String COLA = "notificacion.transferencias";
	public static final String PATRON = "transferencia.*";

	@Bean
	Declarables topologia() {
		TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();

		// LAB S09: Dead Letter Exchange (DLX) y Dead Letter Queue (DLQ)
		DirectExchange dlx = ExchangeBuilder.directExchange("transferencias.dlx").durable(true).build();
		Queue dlq = QueueBuilder.durable("notificacion.transferencias.dlq").build();

		// La cola principal ahora apunta a la DLX
		Queue cola = QueueBuilder.durable(COLA)
			.deadLetterExchange("transferencias.dlx")
			.deadLetterRoutingKey("notificacion.transferencias")
			.build();

		return new Declarables(
			transferencias,
			cola,
			BindingBuilder.bind(cola).to(transferencias).with(PATRON),
			dlx,
			dlq,
			BindingBuilder.bind(dlq).to(dlx).with("notificacion.transferencias")
		);
	}
}
