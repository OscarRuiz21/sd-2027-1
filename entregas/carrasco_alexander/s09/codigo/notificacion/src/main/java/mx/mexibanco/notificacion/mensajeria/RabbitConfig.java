package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "transferencias";
    public static final String COLA = "notificacion.transferencias";
    public static final String PATRON = "transferencia.*";

    // Dead letter: a donde van los mensajes que se rechazan sin reencolar.
    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";
    public static final String DLQ_CLAVE = "notificacion.transferencias.dlq";

    // Mismo exchange que declara transferencia, con los mismos parametros (topic, durable).
    @Bean
    public TopicExchange transferenciasExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // Cola principal, con su dead letter exchange y la clave con la que se reenvian los rechazados.
    @Bean
    public Queue colaNotificaciones() {
        return QueueBuilder.durable(COLA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_CLAVE)
                .build();
    }

    @Bean
    public Binding bindingNotificaciones() {
        return BindingBuilder.bind(colaNotificaciones()).to(transferenciasExchange()).with(PATRON);
    }

    // El DLX, la DLQ y el binding que las une.
    @Bean
    public DirectExchange transferenciasDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue colaMuerta() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding bindingColaMuerta() {
        return BindingBuilder.bind(colaMuerta()).to(transferenciasDlx()).with(DLQ_CLAVE);
    }

   
}