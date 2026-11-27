package mx.mexibanco.notificacion.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * LAB S09: el evento del contrato, visto desde el consumidor. Es una COPIA del record de
 * transferencia (no se comparte codigo entre servicios, solo el contrato JSON). Los campos que
 * sobren en el JSON se ignoran.
 */
public record EventoTransferencia(
	String eventoId,
	String tipo,
	Long transferenciaId,
	String clabeOrigen,
	String clabeDestino,
	BigDecimal monto,
	BigDecimal saldoOrigen,
	BigDecimal saldoDestino,
	Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";

	/** Lo minimo para poder avisar. Si falta algo, el mensaje no se puede procesar nunca: va a la DLQ. */
	public void validar() {
		if (eventoId == null || eventoId.isBlank()) {
			throw new EventoIlegibleException("Falta eventoId");
		}
		if (!REALIZADA.equals(tipo) && !REVERTIDA.equals(tipo)) {
			throw new EventoIlegibleException("Tipo de evento desconocido: " + tipo);
		}
		if (transferenciaId == null || clabeOrigen == null || clabeDestino == null || monto == null) {
			throw new EventoIlegibleException("Faltan datos de la transferencia en el evento " + eventoId);
		}
	}
}
