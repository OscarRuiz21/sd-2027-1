package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionRabbit {

    public static final String COLA = "notificacion.transferencias";
    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";
    public static final String CLAVE_DLQ = "notificacion.transferencias.dlq";

    @Bean
    public TopicExchange exchangeTransferencias() {
        return new TopicExchange("transferencias", true, false);
    }

    @Bean
    public Queue colaTransferencias() {
        return QueueBuilder.durable(COLA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", CLAVE_DLQ)
                .build();
    }

    @Bean
    public Binding enlaceTransferencias(
            @Qualifier("colaTransferencias") Queue cola,
            TopicExchange exchangeTransferencias) {

        return BindingBuilder.bind(cola)
                .to(exchangeTransferencias)
                .with("transferencia.*");
    }

    @Bean
    public DirectExchange exchangeErrores() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue colaErrores() {
        return new Queue(DLQ, true);
    }

    @Bean
    public Binding enlaceErrores(
            @Qualifier("colaErrores") Queue cola,
            DirectExchange exchangeErrores) {

        return BindingBuilder.bind(cola)
                .to(exchangeErrores)
                .with(CLAVE_DLQ);
    }
}