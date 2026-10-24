
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

    public static final String EXCHANGE_DLQ = "transferencias.dlx";
    public static final String COLA_DLQ = "notificacion.transferencias.dlq";
    public static final String CLAVE_DLQ = "notificacion.transferencias";

    @Bean
    Declarables topologia() {
        TopicExchange transferencias =
            ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();

        DirectExchange dlx =
            ExchangeBuilder.directExchange(EXCHANGE_DLQ).durable(true).build();

        Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange(EXCHANGE_DLQ)
            .deadLetterRoutingKey(CLAVE_DLQ)
            .build();

        Queue colaDlq = QueueBuilder.durable(COLA_DLQ).build();

        return new Declarables(
            transferencias,
            dlx,
            cola,
            colaDlq,
            BindingBuilder.bind(cola).to(transferencias).with(PATRON),
            BindingBuilder.bind(colaDlq).to(dlx).with(CLAVE_DLQ)
        );
    }
}
