package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String COLA = "notificacion.transferencias";
    public static final String DLX = "transferencias.dlx";
    public static final String DLQ = "notificacion.transferencias.dlq";

    @Bean
    public TopicExchange transferenciasExchange() {
        return new TopicExchange("transferencias", true, false);
    }

    @Bean
    public Queue notificacionTransferencias() {
        return new Queue(COLA, true, false, false, Map.of(
            "x-dead-letter-exchange", DLX,
            "x-dead-letter-routing-key", COLA));
    }

    @Bean
    public Binding transferenciasBinding(@Qualifier("notificacionTransferencias") Queue notificacionTransferencias,
                                         TopicExchange transferenciasExchange) {
        return BindingBuilder.bind(notificacionTransferencias)
            .to(transferenciasExchange).with("transferencia.*");
    }

    @Bean
    public DirectExchange transferenciasDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue notificacionTransferenciasDlq() {
        // Sin consumidor ni retorno automatico a la principal: conserva el mensaje para inspeccion.
        return new Queue(DLQ, true, false, false);
    }

    @Bean
    public Binding transferenciasDlqBinding(
            @Qualifier("notificacionTransferenciasDlq") Queue cola,
            @Qualifier("transferenciasDlx") DirectExchange dlx) {
        return BindingBuilder.bind(cola).to(dlx).with(COLA);
    }
}
