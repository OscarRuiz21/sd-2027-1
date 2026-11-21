package mx.mexibanco.notificacion.mensajeria;
import org.springframework.context.annotation.*;
import org.springframework.amqp.core.*;
@Configuration
public class ConfiguracionRabbit {
    @Bean Declarables topologiaTransferencias() {
        TopicExchange exchange = new TopicExchange("transferencias", true, false);
        DirectExchange dlx = new DirectExchange("transferencias.dlx", true, false);
        Queue cola = QueueBuilder.durable("notificacion.transferencias")
            .deadLetterExchange("transferencias.dlx")
            .deadLetterRoutingKey("notificacion.transferencias").build();
        Queue dlq = QueueBuilder.durable("notificacion.transferencias.dlq").build();
        return new Declarables(exchange, dlx, cola, dlq,
            BindingBuilder.bind(cola).to(exchange).with("transferencia.*"),
            BindingBuilder.bind(dlq).to(dlx).with("notificacion.transferencias"));
    }
}
