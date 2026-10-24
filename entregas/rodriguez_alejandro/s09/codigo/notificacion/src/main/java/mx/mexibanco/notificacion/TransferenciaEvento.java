package mx.mexibanco.notificacion;

import java.math.BigDecimal;

public record TransferenciaEvento(
    String eventoId,
    String tipo,
    Long transferenciaId,
    String clabeOrigen,
    String clabeDestino,
    BigDecimal monto,
    BigDecimal saldoOrigen,
    BigDecimal saldoDestino,
    String fecha
) {}