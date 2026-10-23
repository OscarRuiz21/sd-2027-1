
package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
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

    @Bean
    Declarables topologia() {
        TopicExchange transferencias =
                ExchangeBuilder.topicExchange(EXCHANGE)
                        .durable(true)
                        .build();

        Queue cola = QueueBuilder.durable(COLA)
                .deadLetterExchange(EXCHANGE_DLQ)
                .deadLetterRoutingKey(COLA)
                .build();

        Binding enlacePrincipal = new Binding(
                COLA,
                Binding.DestinationType.QUEUE,
                EXCHANGE,
                PATRON,
                null
        );

        var exchangeFallidos =
                ExchangeBuilder.directExchange(EXCHANGE_DLQ)
                        .durable(true)
                        .build();

        Queue colaFallidos = QueueBuilder.durable(COLA_DLQ).build();

        Binding enlaceFallidos = new Binding(
                COLA_DLQ,
                Binding.DestinationType.QUEUE,
                EXCHANGE_DLQ,
                COLA,
                null
        );

        return new Declarables(
                transferencias,
                cola,
                enlacePrincipal,
                exchangeFallidos,
                colaFallidos,
                enlaceFallidos
        );
    }
}
