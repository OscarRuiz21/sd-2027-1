package mx.mexibanco.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;

/** Contrato JSON del laboratorio S09; no confundir clabe con clave del API HTTP. */
public record EventoTransferencia(
    String eventoId,
    String tipo,
    Long transferenciaId,
    String clabeOrigen,
    String clabeDestino,
    BigDecimal monto,
    BigDecimal saldoOrigen,
    BigDecimal saldoDestino,
    Instant fecha
) {}
