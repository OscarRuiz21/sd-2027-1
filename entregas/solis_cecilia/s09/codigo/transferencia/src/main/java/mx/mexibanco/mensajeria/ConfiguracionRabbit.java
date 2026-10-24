package mx.mexibanco.mensajeria;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionRabbit {

    public static final String EXCHANGE = "transferencias";

    @Bean
    public TopicExchange exchangeTransferencias() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter convertidorJson() {
        return new Jackson2JsonMessageConverter();
    }
}