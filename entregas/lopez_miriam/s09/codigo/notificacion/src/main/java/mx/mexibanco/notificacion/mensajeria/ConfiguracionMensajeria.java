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

    // LAB S09: infraestructura para mensajes que no pueden procesarse.
    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";
    public static final String ROUTING_KEY_DLQ = COLA;

    @Bean
    Declarables topologia() {

        // Exchange principal.
        TopicExchange transferencias =
                ExchangeBuilder.topicExchange(EXCHANGE)
                        .durable(true)
                        .build();

        // Exchange de mensajes rechazados.
        DirectExchange deadLetterExchange =
                ExchangeBuilder.directExchange(DLX)
                        .durable(true)
                        .build();

        // Cola principal: los mensajes rechazados sin requeue
        // se enviaran al DLX.
        Queue cola = QueueBuilder.durable(COLA)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();

        // Cola durable para mensajes invalidos.
        Queue deadLetterQueue =
                QueueBuilder.durable(DLQ).build();

        return new Declarables(
                transferencias,
                deadLetterExchange,
                cola,
                deadLetterQueue,

                BindingBuilder.bind(cola)
                        .to(transferencias)
                        .with(PATRON),

                BindingBuilder.bind(deadLetterQueue)
                        .to(deadLetterExchange)
                        .with(ROUTING_KEY_DLQ)
        );
    }
}
