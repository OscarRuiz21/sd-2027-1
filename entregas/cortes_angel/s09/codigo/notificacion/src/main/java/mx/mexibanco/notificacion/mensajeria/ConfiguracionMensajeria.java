package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionMensajeria {

    public static final String EXCHANGE = "transferencias";
    public static final String COLA = "notificacion.transferencias";
    public static final String PATRON = "transferencia.*";

    @Bean
    Declarables topologia() {
        TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
        
        // NUEVO S09: Cola principal apuntando al basurero (DLX)
        Queue cola = QueueBuilder.durable(COLA)
            .deadLetterExchange("transferencias.dlx")
            .deadLetterRoutingKey(COLA)
            .build();

        // NUEVO S09: El basurero (Exchange Directo y su Cola)
        DirectExchange dlx = ExchangeBuilder.directExchange("transferencias.dlx").durable(true).build();
        Queue dlq = QueueBuilder.durable(COLA + ".dlq").build();

        return new Declarables(
            transferencias, 
            cola, 
            BindingBuilder.bind(cola).to(transferencias).with(PATRON),
            dlx, 
            dlq, 
            BindingBuilder.bind(dlq).to(dlx).with(COLA)
        );
    }
}