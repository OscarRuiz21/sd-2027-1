package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionMensajeria {

    public static final String EXCHANGE = "transferencias";
    public static final String COLA = "notificacion.transferencias";
    public static final String PATRON = "transferencia.*";

    @Bean
Declarables topologia() {
    TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    
    // 1. A la cola principal le agregamos a dónde mandar los mensajes rechazados
    Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange("transferencias.dlx")
            .deadLetterRoutingKey("notificacion.transferencias")
            .build();
            
    // 2. Declaramos el Dead Letter Exchange (DLX) y la Dead Letter Queue (DLQ)
    DirectExchange dlx = ExchangeBuilder.directExchange("transferencias.dlx").durable(true).build();
    Queue dlq = QueueBuilder.durable("notificacion.transferencias.dlq").build();

    return new Declarables(
            transferencias, 
            cola, 
            BindingBuilder.bind(cola).to(transferencias).with(PATRON),
            // Enlazamos la DLQ al DLX
            dlx, 
            dlq, 
            BindingBuilder.bind(dlq).to(dlx).with("notificacion.transferencias")
    );
}
}