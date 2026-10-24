package mx.mexibanco.notificacion;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange transferenciasExchange() {
        return new TopicExchange("transferencias");
    }

    // 1. Declaramos la cola con el nombre exacto que pide el profesor
    @Bean
    public Queue notificacionesQueue() {
        return QueueBuilder.durable("notificacion.transferencias")
                .withArgument("x-dead-letter-exchange", "transferencias.dlx")
                .withArgument("x-dead-letter-routing-key", "transferencias.dlq")
                .build();
    }

    // 2. El binding usa el patrón transferencia.* para atrapar realizadas y revertidas
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(notificacionesQueue()).to(transferenciasExchange()).with("transferencia.*");
    }

    @Bean
    public DirectExchange dlx() {
        return new DirectExchange("transferencias.dlx");
    }

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable("notificacion.transferencias.dlq").build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlq()).to(dlx()).with("transferencias.dlq");
    }
}