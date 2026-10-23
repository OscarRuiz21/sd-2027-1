package mx.mexibanco.notificacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato local del evento publicado por transferencia. */
public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen,
                                  String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen,
                                  BigDecimal saldoDestino, Instant fecha) {
    public static final String REALIZADA = "TransferenciaRealizada";
    public static final String REVERTIDA = "TransferenciaRevertida";

    public void validar() {
        if (eventoId == null || transferenciaId == null || transferenciaId <= 0 ||
                clabeOrigen == null || clabeOrigen.isBlank() ||
                clabeDestino == null || clabeDestino.isBlank() ||
                monto == null || monto.signum() <= 0 || saldoOrigen == null || fecha == null ||
                (!REALIZADA.equals(tipo) && !REVERTIDA.equals(tipo)) ||
                (REALIZADA.equals(tipo) && saldoDestino == null)) {
            throw new IllegalArgumentException("Evento de transferencia invalido");
        }
    }
}
