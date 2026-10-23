package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange; // <-- ESTE ES EL QUE FALTABA
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
        // 1. El exchange principal
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        
        // 2. El Dead Letter Exchange (DLX) y la Dead Letter Queue (DLQ)
        DirectExchange dlx = ExchangeBuilder.directExchange("transferencias.dlx").durable(true).build();
        Queue dlq = QueueBuilder.durable("notificacion.transferencias.dlq").build();

        // 3. La cola principal ahora configurada para mandar rechazos al DLX
        Queue cola = QueueBuilder.durable(COLA)
                .deadLetterExchange("transferencias.dlx")             // x-dead-letter-exchange
                .deadLetterRoutingKey("notificacion.transferencias")  // x-dead-letter-routing-key
                .build();

        return new Declarables(
            transferencias, 
            dlx, 
            dlq, 
            cola,
            BindingBuilder.bind(cola).to(transferencias).with(PATRON),
            BindingBuilder.bind(dlq).to(dlx).with("notificacion.transferencias")
        );
    }
}