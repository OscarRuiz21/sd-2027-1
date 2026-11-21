package mx.mexibanco.notificacion.mensajeria;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration public class NotificacionRabbitConfig {
 @Bean TopicExchange transferenciasExchange(){return new TopicExchange("transferencias",true,false);}
 @Bean DirectExchange transferenciasDlx(){return new DirectExchange("transferencias.dlx",true,false);}
 @Bean Queue notificacionTransferenciasDlq(){return QueueBuilder.durable("notificacion.transferencias.dlq").build();}
 @Bean Binding dlqBinding(){return BindingBuilder.bind(notificacionTransferenciasDlq()).to(transferenciasDlx()).with("notificacion.transferencias");}
 @Bean Queue notificacionTransferencias(){return QueueBuilder.durable("notificacion.transferencias").deadLetterExchange("transferencias.dlx").deadLetterRoutingKey("notificacion.transferencias").build();}
 @Bean Binding transferenciasBinding(){return BindingBuilder.bind(notificacionTransferencias()).to(transferenciasExchange()).with("transferencia.*");}
 @Bean Jackson2JsonMessageConverter rabbitJsonConverter(){return new Jackson2JsonMessageConverter();}
 @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory cf){SimpleRabbitListenerContainerFactory f=new SimpleRabbitListenerContainerFactory(); f.setConnectionFactory(cf); f.setAcknowledgeMode(AcknowledgeMode.MANUAL); return f;}
}
