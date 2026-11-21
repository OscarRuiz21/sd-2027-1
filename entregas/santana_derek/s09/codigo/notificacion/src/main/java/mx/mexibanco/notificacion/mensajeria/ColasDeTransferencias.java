package mx.mexibanco.notificacion.mensajeria;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class ColasDeTransferencias {
 @Bean Declarables colas(@Value("${DLQ_ACTIVA:true}") boolean dlq) {
  TopicExchange exchange=new TopicExchange("transferencias",true,false);
  QueueBuilder builder=QueueBuilder.durable("notificacion.transferencias");
  if(dlq) builder.deadLetterExchange("transferencias.dlx").deadLetterRoutingKey("notificacion.transferencias");
  Queue cola=builder.build();
  DirectExchange dlx=new DirectExchange("transferencias.dlx",true,false);
  Queue muertas=QueueBuilder.durable("notificacion.transferencias.dlq").build();
  return new Declarables(exchange,cola,BindingBuilder.bind(cola).to(exchange).with("transferencia.*"),
   dlx,muertas,BindingBuilder.bind(muertas).to(dlx).with("notificacion.transferencias"));
 }
}
