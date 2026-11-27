package mx.mexibanco.transferencia.mensajeria;

import mx.mexibanco.transferencia.Transferencia;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * LAB S09: el HECHO que publica la transferencia en el exchange "transferencias". Es el contrato del
 * lab, campo por campo; cualquier consumidor (notificacion u otro) lo lee como JSON.
 *
 * eventoId es la llave de idempotencia del consumidor. Se DERIVA del id de la transferencia y del
 * tipo (UUID v3 de "transferencia-42-TransferenciaRealizada"), asi que es el mismo aunque el evento
 * se vuelva a publicar (un reintento del relay, o la saga que vuelve a llegar al mismo punto).
 *
 * En TransferenciaRevertida el dinero no llego a destino: saldoDestino va en null y saldoOrigen es
 * el saldo de origen DESPUES de la devolucion.
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

	/** La saga llego a COMPLETADA. */
	public static EventoTransferencia realizada(Transferencia t, BigDecimal saldoDestino) {
		return de(t, REALIZADA, t.getSaldoOrigenTrasCargo(), saldoDestino);
	}

	/** La saga llego a COMPENSADA: el cargo se devolvio a origen. */
	public static EventoTransferencia revertida(Transferencia t, BigDecimal saldoOrigenTrasDevolucion) {
		return de(t, REVERTIDA, saldoOrigenTrasDevolucion, null);
	}

	private static EventoTransferencia de(Transferencia t, String tipo, BigDecimal saldoOrigen, BigDecimal saldoDestino) {
		String eventoId = UUID.nameUUIDFromBytes(("transferencia-" + t.getId() + "-" + tipo).getBytes(StandardCharsets.UTF_8)).toString();
		return new EventoTransferencia(eventoId, tipo, t.getId(), t.getClaveOrigen(), t.getClaveDestino(), t.getMonto(),
			saldoOrigen, saldoDestino, Instant.now().truncatedTo(ChronoUnit.SECONDS));
	}

	/** La routing key del contrato para cada tipo. */
	public static String routingKeyDe(String tipo) {
		return REVERTIDA.equals(tipo) ? ConfiguracionMensajeria.CLAVE_REVERTIDA : ConfiguracionMensajeria.CLAVE_REALIZADA;
	}
}
