package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ColasTransferencias {

	static final String COLA = "notificacion.transferencias";
	private static final String DLX = "transferencias.dlx";

	@Bean
	Declarables topologiaTransferencias() {
		TopicExchange exchange = new TopicExchange("transferencias", true, false);
		DirectExchange descartes = new DirectExchange(DLX, true, false);
		Queue avisos = QueueBuilder.durable(COLA).deadLetterExchange(DLX).deadLetterRoutingKey(COLA).build();
		Queue dlq = QueueBuilder.durable(COLA + ".dlq").build();
		return new Declarables(exchange, avisos, BindingBuilder.bind(avisos).to(exchange).with("transferencia.*"),
			descartes, dlq, BindingBuilder.bind(dlq).to(descartes).with(COLA));
	}
}
