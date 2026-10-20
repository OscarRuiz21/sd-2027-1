package mx.mexibanco.notificacion.mensajeria;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ConsumidorDeTransferenciasTest {
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 private Message mensaje(String body) {
  MessageProperties p=new MessageProperties();p.setDeliveryTag(7L);
  return new Message(body.getBytes(StandardCharsets.UTF_8),p);
 }
 private String valido() {
  return "{\"eventoId\":\"8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c\",\"tipo\":\"TransferenciaRealizada\",\"transferenciaId\":1,\"clabeOrigen\":\"a\",\"clabeDestino\":\"b\",\"monto\":1,\"saldoOrigen\":9,\"saldoDestino\":11,\"fecha\":\"2026-10-19T22:00:00Z\"}";
 }
 @Test void ackSoloDespuesDeGuardar() throws Exception {
  GuardarAvisos guardar=mock(GuardarAvisos.class);Channel channel=mock(Channel.class);
  when(guardar.procesar(any())).thenReturn(2);
  new ConsumidorDeTransferencias(mapper,guardar,true).recibir(mensaje(valido()),channel);
  var orden=inOrder(guardar,channel);
  orden.verify(guardar).procesar(any());orden.verify(channel).basicAck(7,false);
  verify(channel,never()).basicNack(anyLong(),anyBoolean(),anyBoolean());
 }
 @Test void venenoNoSeReencolaNiLlegaALaBase() throws Exception {
  GuardarAvisos guardar=mock(GuardarAvisos.class);Channel channel=mock(Channel.class);
  new ConsumidorDeTransferencias(mapper,guardar,true).recibir(mensaje("{\"hola\":"),channel);
  verifyNoInteractions(guardar);verify(channel).basicNack(7,false,false);
 }
 @Test void tipoDesconocidoTambienEsVeneno() throws Exception {
  GuardarAvisos guardar=mock(GuardarAvisos.class);Channel channel=mock(Channel.class);
  new ConsumidorDeTransferencias(mapper,guardar,true).recibir(mensaje(valido().replace("TransferenciaRealizada","Desconocido")),channel);
  verifyNoInteractions(guardar);verify(channel).basicNack(7,false,false);
 }
 @Test void falloTransitorioSeReencolaSinAck() throws Exception {
  GuardarAvisos guardar=mock(GuardarAvisos.class);Channel channel=mock(Channel.class);
  when(guardar.procesar(any())).thenThrow(new IllegalStateException("Base caída"));
  new ConsumidorDeTransferencias(mapper,guardar,true).recibir(mensaje(valido()),channel);
  verify(channel).basicNack(7,false,true);verify(channel,never()).basicAck(anyLong(),anyBoolean());
 }
}
