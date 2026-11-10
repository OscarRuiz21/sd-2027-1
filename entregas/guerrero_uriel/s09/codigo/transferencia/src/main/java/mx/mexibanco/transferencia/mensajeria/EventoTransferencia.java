package mx.mexibanco.transferencia.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;

public record EventoTransferencia(String eventoId, String tipo, Long transferenciaId, String clabeOrigen,
                                  String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen,
                                  BigDecimal saldoDestino, Instant fecha) {
}
