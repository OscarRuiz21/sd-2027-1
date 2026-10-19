package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * S09: transferencia solo necesita el EXCHANGE. Publica ahi y no sabe que colas hay detras: las colas
 * son de quien consume. Al conectarse, Spring le pide al broker que exista cada @Bean de tipo
 * exchange; si ya existe con las mismas propiedades, no pasa nada.
 */
@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";

	/** Topic: el consumidor elige con patrones. Durable: sobrevive a un reinicio del broker. */
	@Bean
	TopicExchange exchangeTransferencias() {
		return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
	}

	/**
	 * Los eventos viajan como JSON. Spring Boot se lo pone al RabbitTemplate por ser el unico
	 * convertidor. Se usa el ObjectMapper de Spring Boot para que fecha salga como texto ISO-8601.
	 */
	@Bean
	MessageConverter convertidorJson(ObjectMapper json) {
		return new Jackson2JsonMessageConverter(json);
	}
}
