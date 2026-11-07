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

    // Variables para la Dead Letter Queue
    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";

    @Bean
    Declarables topologia() {
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        
        // 1. Cola principal AHORA CON ARGUMENTOS apuntando a la DLQ
        Queue cola = QueueBuilder.durable(COLA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", COLA)
                .build();

        // 2. Declaramos el exchange y la cola de mensajes muertos
        DirectExchange deadLetterExchange = ExchangeBuilder.directExchange(DLX).durable(true).build();
        Queue deadLetterQueue = QueueBuilder.durable(DLQ).build();

        return new Declarables(
                transferencias, 
                cola, 
                BindingBuilder.bind(cola).to(transferencias).with(PATRON),
                // Enlazamos la DLQ a su DLX
                deadLetterExchange,
                deadLetterQueue,
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(COLA)
        );
    }
}