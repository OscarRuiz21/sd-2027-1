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
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();

        // La cola principal, ahora con dos argumentos que dicen a donde se va un mensaje rechazado.
        Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange(DLX)            // x-dead-letter-exchange
            .deadLetterRoutingKey(COLA)         // x-dead-letter-routing-key
            .build();

        // El DLX (direct, durable) y la DLQ (durable), enlazados con la clave notificacion.transferencias.
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue dlq = QueueBuilder.durable(DLQ).build();

        return new Declarables(
            transferencias, cola, BindingBuilder.bind(cola).to(transferencias).with(PATRON),
            dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with(COLA));
    }
}