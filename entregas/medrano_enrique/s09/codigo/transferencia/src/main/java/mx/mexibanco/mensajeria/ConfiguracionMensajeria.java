package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionMensajeria {

    public static final String EXCHANGE = "transferencias";

    /**
     * Topic: el consumidor elige con patrones. Durable: sobrevive a un reinicio del
     * broker.
     */
    @Bean
    TopicExchange exchangeTransferencias() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    /**
     * Los eventos viajan como JSON. Spring Boot se lo pone al RabbitTemplate por
     * ser el único convertidor.
     */
    @Bean
    MessageConverter convertidorJson(ObjectMapper json) {
        return new Jackson2JsonMessageConverter(json);
    }
}