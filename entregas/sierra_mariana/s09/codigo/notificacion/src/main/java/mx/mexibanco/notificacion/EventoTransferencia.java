package mx.mexibanco.notificacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventoTransferencia(
    UUID eventoId,
    String tipo,
    Long transferenciaId,
    String claveOrigen,
    String claveDestino,
    BigDecimal monto,
    BigDecimal saldoOrigen,
    BigDecimal saldoDestino,
    Instant fecha
) {}