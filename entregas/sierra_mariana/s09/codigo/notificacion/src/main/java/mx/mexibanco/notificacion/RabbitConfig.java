package mx.mexibanco.notificacion;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

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
        return BindingBuilder.bind(dlq()).to(dlx()).with("transferencia.envenenada"); 
    }

    @Bean
    public Queue transferenciasQueue() {
        return QueueBuilder.durable("notificaciones.transferencias")
                .withArgument("x-dead-letter-exchange", "transferencias.dlx")
                .withArgument("x-dead-letter-routing-key", "transferencia.envenenada")
                .build();
    }
}