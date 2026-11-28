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

    // Tarea 6: a donde va un mensaje que el consumidor rechaza sin reencolar (nack, requeue=false).
    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";
    public static final String CLAVE_DLQ = "notificacion.transferencias";

    @Bean
    Declarables topologia() {
        // El mismo exchange que declara transferencia, con las mismas propiedades (topic, durable).
        // Se declara aqui tambien para poder enlazar la cola aunque notificacion arranque primero.
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();

        // Durable: la cola y sus mensajes persistentes sobreviven a un reinicio del broker.
        // Los dos argumentos le dicen al BROKER a donde mover un mensaje rechazado: es RabbitMQ quien
        // lo mueve, no este servicio. Ojo: RabbitMQ no deja cambiar los argumentos de una cola que ya
        // existe (PRECONDITION_FAILED): si la cola se creo antes sin ellos, hay que borrarla.
        Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange(DLX)              // x-dead-letter-exchange
            .deadLetterRoutingKey(CLAVE_DLQ)      // x-dead-letter-routing-key
            .build();

        // El DLX es direct: la DLQ recibe exactamente la clave notificacion.transferencias.
        DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
        Queue dlq = QueueBuilder.durable(DLQ).build();

        return new Declarables(
            transferencias, cola, BindingBuilder.bind(cola).to(transferencias).with(PATRON),
            dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with(CLAVE_DLQ));
    }
}
