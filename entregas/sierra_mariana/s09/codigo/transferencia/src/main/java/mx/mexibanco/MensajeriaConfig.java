package mx.mexibanco;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MensajeriaConfig {

    // 1. Declara el exchange "transferencias" (topic, durable)
    @Bean
    public TopicExchange transferenciasExchange() {
        // El segundo parámetro en true significa que es "durable"
        return new TopicExchange("transferencias", true, false); 
    }

    // 2. Configura el convertidor JSON para el RabbitTemplate
    @Bean
    public Jackson2JsonMessageConverter producerJackson2MessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}