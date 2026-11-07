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
        // El mismo exchange que declara transferencia, con las mismas propiedades (topic, durable).
        // Se declara aquí también para poder enlazar la cola aunque notificacion arranque primero.
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        // Durable: la cola y sus mensajes persistentes sobreviven a un reinicio del broker.
        // Tarea 6: un mensaje rechazado sin reencolar (basicNack con requeue=false) no se tira: el broker
        // lo manda al DLX con esta routing key. Cambiar estos argumentos en una cola que ya existe da
        // PRECONDITION_FAILED: hay que borrarla desde el panel.
        Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange(DLX)            // x-dead-letter-exchange
            .deadLetterRoutingKey(COLA)         // x-dead-letter-routing-key
            .build();
        // El DLX (direct) y la DLQ, donde los mensajes envenenados esperan a que una persona los revise.
        DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(transferencias, cola, BindingBuilder.bind(cola).to(transferencias).with(PATRON),
            dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with(COLA));
    }
}
