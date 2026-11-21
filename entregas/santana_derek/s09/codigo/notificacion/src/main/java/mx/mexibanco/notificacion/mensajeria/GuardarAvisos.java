package mx.mexibanco.notificacion.mensajeria;
import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GuardarAvisos {
 private final JdbcTemplate jdbc;
 private final NotificacionRepository avisos;
 private final boolean idempotencia;
 public GuardarAvisos(JdbcTemplate jdbc,NotificacionRepository avisos,@Value("${IDEMPOTENCIA_ACTIVA:true}") boolean idempotencia) {
  this.jdbc=jdbc;this.avisos=avisos;this.idempotencia=idempotencia;
 }
 @Transactional
 public int procesar(EventoTransferencia e) {
  // La llave única y los avisos se confirman o revierten en LA MISMA transacción.
  if(idempotencia && jdbc.update("INSERT INTO evento_recibido(evento_id) VALUES (?) ON CONFLICT DO NOTHING",e.eventoId())==0) return 0;
  if(e.tipo().equals("TransferenciaRevertida")) {
   avisos.save(new Notificacion(e.clabeOrigen(),"Tu transferencia "+e.transferenciaId()+" no se realizó y te devolvimos "+e.monto()));return 1;
  }
  avisos.save(new Notificacion(e.clabeOrigen(),"Transferencia "+e.transferenciaId()+" enviada por "+e.monto()+"; saldo "+e.saldoOrigen()));
  avisos.save(new Notificacion(e.clabeDestino(),"Transferencia "+e.transferenciaId()+" recibida por "+e.monto()+"; saldo "+e.saldoDestino()));
  return 2;
 }
}
