package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.Binding;
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

    public static final String EXCHANGE_DLX = "transferencias.dlx";
    public static final String COLA_DLQ = "notificacion.transferencias.dlq";
    public static final String ROUTING_KEY_DLQ = "notificacion.transferencias";

    @Bean
    Declarables topologia() {
        // Exchange principal (topic, durable)
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();

        // Cola principal configurada con DLX y DLQ routing key
        Queue cola = QueueBuilder.durable(COLA)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();

        Binding bindingPrincipal = BindingBuilder.bind(cola).to(transferencias).with(PATRON);

        // Dead Letter Exchange (direct, durable) y Dead Letter Queue (durable)
        DirectExchange dlx = ExchangeBuilder.directExchange(EXCHANGE_DLX).durable(true).build();
        Queue dlq = QueueBuilder.durable(COLA_DLQ).build();
        Binding bindingDlq = BindingBuilder.bind(dlq).to(dlx).with(ROUTING_KEY_DLQ);

        return new Declarables(transferencias, cola, bindingPrincipal, dlx, dlq, bindingDlq);
    }
}
