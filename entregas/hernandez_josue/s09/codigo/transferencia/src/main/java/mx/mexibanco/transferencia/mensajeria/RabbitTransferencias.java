package mx.mexibanco.transferencia.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitTransferencias {

	static final String EXCHANGE = "transferencias";

	@Bean
	TopicExchange exchangeTransferencias() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@Bean
	Jackson2JsonMessageConverter jsonRabbit(ObjectMapper mapper) {
		return new Jackson2JsonMessageConverter(mapper);
	}
}
