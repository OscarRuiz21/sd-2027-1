package mx.mexibanco.mensajeria;
import org.springframework.context.annotation.*;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
@Configuration
public class ConfiguracionRabbit {
    @Bean TopicExchange transferencias() { return new TopicExchange("transferencias", true, false); }
    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory conexion, ObjectMapper mapper) {
        RabbitTemplate template = new RabbitTemplate(conexion);
        template.setMessageConverter(new Jackson2JsonMessageConverter(mapper));
        template.setMandatory(true);
        return template;
    }
}
