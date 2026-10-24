package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "transferencias";
    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    @Bean
    public TopicExchange transferenciasExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter conversorJson(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(
            mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Bean
    public RabbitTemplate rabbitTemplate(RabbitTemplateConfigurer configurer,
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter conversorJson) {
        RabbitTemplate template = new RabbitTemplate();
        configurer.configure(template, connectionFactory);
        template.setMessageConverter(conversorJson);
        template.setMandatory(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            String id = correlation == null ? "sin-id" : correlation.getId();
            if (ack) {
                log.info("PUBLISHER CONFIRM eventoId={} ack=true (no confirma consumo)", id);
            } else {
                log.error("PUBLISHER CONFIRM eventoId={} ack=false causa={}", id, cause);
            }
        });
        template.setReturnsCallback(returned -> log.warn(
            "EVENTO SIN COLA eventoId={} exchange={} routingKey={} motivo={}",
            returned.getMessage().getMessageProperties().getMessageId(),
            returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
