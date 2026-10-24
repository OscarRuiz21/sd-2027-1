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
    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";

    @Bean
    Declarables topologia() {
        TopicExchange exchange = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
        Queue cola = QueueBuilder.durable(COLA)
                .deadLetterExchange(DLX).deadLetterRoutingKey(COLA).build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange, dlx, cola, dlq,
                BindingBuilder.bind(cola).to(exchange).with(PATRON),
                BindingBuilder.bind(dlq).to(dlx).with(COLA));
    }
}