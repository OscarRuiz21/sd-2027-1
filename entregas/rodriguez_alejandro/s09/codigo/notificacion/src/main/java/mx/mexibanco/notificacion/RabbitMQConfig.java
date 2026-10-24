package mx.mexibanco.notificacion;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Dead Letter Exchange (tipo direct)
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("transferencias.dlx");
    }

    // Dead Letter Queue (DLQ)
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("notificacion.transferencias.dlq").build();
    }

    // se enlaza la DLQ con el DLX usando el routing key específico
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("notificacion.transferencias");
    }

    // Esta es la cola principal, configurada para enviar los mensajes fallidos a la DLQ
    @Bean
    public Queue notificacionQueue() {
        return QueueBuilder.durable("notificacion.transferencias")
                .withArgument("x-dead-letter-exchange", "transferencias.dlx")
                .withArgument("x-dead-letter-routing-key", "notificacion.transferencias")
                .build();
    }

    // Exchange principal
    @Bean
    public TopicExchange transferenciasExchange() {
        return new TopicExchange("transferencias", true, false);
    }

    // Binding principal
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(notificacionQueue()).to(transferenciasExchange()).with("transferencia.*");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}