package mx.mexibanco.transferencia.mensajeria;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import mx.mexibanco.transferencia.Transferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PublicadorDeEventos {
 private static final Logger log=LoggerFactory.getLogger(PublicadorDeEventos.class);
 private final RabbitTemplate rabbit;
 public PublicadorDeEventos(ConnectionFactory conexion,ObjectMapper mapper) {
  rabbit=new RabbitTemplate(conexion);
  rabbit.setMessageConverter(new Jackson2JsonMessageConverter(mapper));
 }
 @Bean TopicExchange transferenciasExchange(){return new TopicExchange("transferencias",true,false);}
 public void publicar(Transferencia t,boolean revertida,BigDecimal saldoOrigen,BigDecimal saldoDestino) {
  String tipo=revertida?"TransferenciaRevertida":"TransferenciaRealizada";
  String id=UUID.nameUUIDFromBytes(("transferencia-"+t.getId()+"-"+tipo).getBytes(StandardCharsets.UTF_8)).toString();
  EventoTransferencia evento=new EventoTransferencia(id,tipo,t.getId(),t.getClaveOrigen(),t.getClaveDestino(),t.getMonto(),saldoOrigen,saldoDestino,t.getActualizada());
  CorrelationData correlacion=new CorrelationData(id);
  try {
   rabbit.convertAndSend("transferencias",revertida?"transferencia.revertida":"transferencia.realizada",evento,
    message->{message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);return message;},correlacion);
   if(!correlacion.getFuture().get(3,TimeUnit.SECONDS).isAck()) throw new IllegalStateException("El broker rechazó el evento");
   log.info("Evento {} {} confirmado por broker",id,tipo);
  } catch(Exception e) {
   if(e instanceof InterruptedException) Thread.currentThread().interrupt();
   // Sin Outbox, publicar y guardar NO son atómicos. No se revierte dinero por un aviso.
   log.error("Evento {} NO PUBLICADO; sin Outbox requiere recuperación: {}",id,e.toString());
  }
 }
}
