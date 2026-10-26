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

    private static final String DLX = "transferencias.dlx";
    private static final String DLQ =
            "notificacion.transferencias.dlq";
    private static final String CLAVE_DLQ =
            "notificacion.transferencias";

    @Bean
    Declarables topologia() {

        TopicExchange transferencias =
            ExchangeBuilder.topicExchange(EXCHANGE)
                .durable(true)
                .build();

        Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(CLAVE_DLQ)
            .build();

        DirectExchange dlx =
            ExchangeBuilder.directExchange(DLX)
                .durable(true)
                .build();

        Queue dlq = QueueBuilder.durable(DLQ).build();

        Binding enlacePrincipal =
            BindingBuilder.bind(cola)
                .to(transferencias)
                .with(PATRON);

        Binding enlaceDLQ =
            BindingBuilder.bind(dlq)
                .to(dlx)
                .with(CLAVE_DLQ);

        return new Declarables(
            transferencias,
            cola,
            enlacePrincipal,
            dlx,
            dlq,
            enlaceDLQ
        );
    }
}