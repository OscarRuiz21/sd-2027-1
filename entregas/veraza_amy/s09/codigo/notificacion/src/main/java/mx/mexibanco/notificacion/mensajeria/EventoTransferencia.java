package mx.mexibanco.notificacion.mensajeria;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId,
    String clabeOrigen, String clabeDestino, BigDecimal monto,
    BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha) {
    public void validar() {
        if (eventoId == null || transferenciaId == null || transferenciaId <= 0 || fecha == null
            || !("TransferenciaRealizada".equals(tipo) || "TransferenciaRevertida".equals(tipo))
            || clabeOrigen == null || !clabeOrigen.matches("[0-9]{18}")
            || clabeDestino == null || !clabeDestino.matches("[0-9]{18}")
            || clabeOrigen.equals(clabeDestino) || monto == null || monto.signum() <= 0
            || saldoOrigen == null || ("TransferenciaRealizada".equals(tipo) && saldoDestino == null)) {
            throw new IllegalArgumentException("Evento de transferencia invalido");
        }
    }
}
