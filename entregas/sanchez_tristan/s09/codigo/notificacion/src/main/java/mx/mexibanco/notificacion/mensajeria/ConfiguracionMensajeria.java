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

    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";
    public static final String DLQ_ROUTING_KEY = "notificacion.transferencias";

    @Bean
    Declarables topologia() {
        // El mismo exchange que declara transferencia, con las mismas propiedades (topic, durable).
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();

        // Cola principal con dead letter exchange y dead letter routing key
        Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(DLQ_ROUTING_KEY)
            .build();
        Binding enlaceCola = BindingBuilder.bind(cola).to(transferencias).with(PATRON);

        // DLX (direct, durable) y la DLQ (durable), enlazadas con notificacion.transferencias
        DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        Binding enlaceDlq = BindingBuilder.bind(dlq).to(dlx).with(DLQ_ROUTING_KEY);

        return new Declarables(transferencias, cola, enlaceCola, dlx, dlq, enlaceDlq);
    }
}