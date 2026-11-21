package mx.mexibanco.notificacion.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;

/** Copia del evento que publica transferencia. Mismos campos, mismo contrato. */
public record TransferenciaEvento(
        String eventoId,
        String tipo,
        Long transferenciaId,
        String clabeOrigen,
        String clabeDestino,
        BigDecimal monto,
        BigDecimal saldoOrigen,
        BigDecimal saldoDestino,
        Instant fecha
) {
}