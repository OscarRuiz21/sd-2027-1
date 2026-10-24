package mx.mexibanco.notificacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lab S09: el evento que publica transferencia, del lado de notificacion. No es la clase de
 * transferencia: es la copia local del contrato, con los mismos nombres de campo del JSON. Igual que
 * con HTTP (AvisoDeMovimiento), cada servicio tiene la suya.
 */
public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen,
								  String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen,
								  BigDecimal saldoDestino, Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";
}
