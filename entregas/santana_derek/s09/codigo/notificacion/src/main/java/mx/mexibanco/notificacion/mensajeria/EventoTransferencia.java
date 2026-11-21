package mx.mexibanco.notificacion.mensajeria;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
public record EventoTransferencia(String eventoId,String tipo,Long transferenciaId,
 String clabeOrigen,String clabeDestino,BigDecimal monto,BigDecimal saldoOrigen,
 BigDecimal saldoDestino,Instant fecha) {
 public void validar() {
  if(eventoId==null) throw new IllegalArgumentException("Falta eventoId");
  UUID.fromString(eventoId);
  if(tipo==null || !Set.of("TransferenciaRealizada","TransferenciaRevertida").contains(tipo)
   || transferenciaId==null || transferenciaId<=0 || clabeOrigen==null || clabeOrigen.isBlank()
   || clabeDestino==null || clabeDestino.isBlank() || monto==null || monto.signum()<=0
   || saldoOrigen==null || fecha==null || (tipo.equals("TransferenciaRealizada") && saldoDestino==null))
   throw new IllegalArgumentException("Evento fuera del contrato");
 }
}
