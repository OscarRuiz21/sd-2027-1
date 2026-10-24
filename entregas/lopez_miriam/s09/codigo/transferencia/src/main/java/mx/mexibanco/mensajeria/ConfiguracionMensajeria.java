
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

    /** Exchange tipo topic y durable. */
    @Bean
    TopicExchange exchangeTransferencias() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    /** Convierte los eventos Java a mensajes JSON. */
    @Bean
    MessageConverter convertidorJson(ObjectMapper json) {
        return new Jackson2JsonMessageConverter(json);
    }
}
