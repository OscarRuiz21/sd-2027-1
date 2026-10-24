package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionMensajeria {

    public static final String EXCHANGE = "transferencias";
    public static final String COLA = "notificacion.transferencias";

    public static final String DLX = "transferencias.dlx";
    public static final String COLA_DLQ = "notificacion.transferencias.dlq";

    @Bean
    public TopicExchange intercambioTransferencias() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange intercambioErrores() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue colaTransferencias() {
        return QueueBuilder.durable(COLA)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(COLA)
                .build();
    }

    @Bean
    public Queue colaMensajesFallidos() {
        return QueueBuilder.durable(COLA_DLQ).build();
    }

    @Bean
    public Binding enlaceTransferencias(
            Queue colaTransferencias,
            TopicExchange intercambioTransferencias
    ) {
        return BindingBuilder.bind(colaTransferencias)
                .to(intercambioTransferencias)
                .with("transferencia.*");
    }

    @Bean
    public Binding enlaceMensajesFallidos(
            Queue colaMensajesFallidos,
            DirectExchange intercambioErrores
    ) {
        return BindingBuilder.bind(colaMensajesFallidos)
                .to(intercambioErrores)
                .with(COLA);
    }
}
