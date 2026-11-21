package mx.mexibanco.notificacion.mensajeria;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
@Component
public class ConsumidorDeTransferencias {
 private static final Logger log=LoggerFactory.getLogger(ConsumidorDeTransferencias.class);
 private final ObjectMapper mapper;
 private final GuardarAvisos guardar;
 private final boolean dlq;
 public ConsumidorDeTransferencias(ObjectMapper mapper,GuardarAvisos guardar,@Value("${DLQ_ACTIVA:true}") boolean dlq) {
  this.mapper=mapper;this.guardar=guardar;this.dlq=dlq;
 }
 @RabbitListener(queues="notificacion.transferencias",ackMode="MANUAL")
 public void recibir(Message message,Channel channel) throws IOException {
  long tag=message.getMessageProperties().getDeliveryTag();
  EventoTransferencia evento;
  try {evento=mapper.readValue(message.getBody(),EventoTransferencia.class);evento.validar();}
  catch(Exception veneno) {
   log.warn("Mensaje inválido: {}; DLQ={}",veneno.getClass().getSimpleName(),dlq);
   if(!dlq) {try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
   channel.basicNack(tag,false,!dlq);return;
  }
  try {
   int nuevos=guardar.procesar(evento); // vuelve después del COMMIT del proxy @Transactional
   channel.basicAck(tag,false);
   log.info("Evento {}: {} aviso(s) nuevo(s); redelivered={}",evento.eventoId(),nuevos,message.getMessageProperties().isRedelivered());
  } catch(RuntimeException transitorio) {
   log.warn("Error al guardar: se reencola {}",evento.eventoId(),transitorio);
   channel.basicNack(tag,false,true);
  }
 }
}
