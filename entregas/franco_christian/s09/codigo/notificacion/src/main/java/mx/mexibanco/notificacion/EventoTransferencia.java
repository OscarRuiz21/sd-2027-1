package mx.mexibanco.notificacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * LAB S09: copia local del contrato del evento. Notificacion no depende de la clase de transferencia;
 * cada servicio tiene su propia copia (igual que con HTTP y los DTOs). Los nombres de los campos deben
 * coincidir EXACTAMENTE con el JSON del contrato.
 */
public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen,
                                  String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen,
                                  BigDecimal saldoDestino, Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";
}
