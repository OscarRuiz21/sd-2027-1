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
        // Exchange principal para los eventos de transferencia.
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE)
                .durable(true)
                .build();

        // Exchange para los mensajes rechazados.
        DirectExchange dlx = ExchangeBuilder.directExchange("transferencias.dlx")
                .durable(true)
                .build();

        // Cola donde se almacenan los mensajes rechazados.
        Queue dlq = QueueBuilder.durable("notificacion.transferencias.dlq")
                .build();

        // Cola principal con la configuracion de Dead Letter Exchange.
        Queue cola = QueueBuilder.durable(COLA)
                .deadLetterExchange("transferencias.dlx")
                .deadLetterRoutingKey("notificacion.transferencias")
                .build();

        return new Declarables(
                transferencias,
                cola,
                BindingBuilder.bind(cola).to(transferencias).with(PATRON),
                dlx,
                dlq,
                BindingBuilder.bind(dlq).to(dlx).with("notificacion.transferencias")
        );
    }
}