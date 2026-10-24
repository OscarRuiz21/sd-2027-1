package mx.mexibanco.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lab S09: el contrato del evento que transferencia deja en el exchange "transferencias". Es un
 * record propio y no la entidad Transferencia: es lo que ve quien escuche, y cambiar una columna de
 * la tabla no debe romper a nadie afuera. Es un HECHO en pasado ("ya se hizo"), no una orden.
 *
 * eventoId nace con la fila de la transferencia y se guarda con ella, asi que si el evento se vuelve
 * a publicar sale con el MISMO id: es la llave de idempotencia de quien lo consume.
 */
public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen,
								  String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen,
								  BigDecimal saldoDestino, Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";

	/** La saga llego a COMPLETADA: el dinero salio de origen y llego a destino. */
	public static EventoTransferencia realizada(UUID eventoId, Long transferenciaId, String clabeOrigen, String clabeDestino,
												BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha) {
		return new EventoTransferencia(eventoId, REALIZADA, transferenciaId, clabeOrigen, clabeDestino, monto,
			saldoOrigen, saldoDestino, fecha);
	}

	/** La saga llego a COMPENSADA: el abono fallo y el cargo ya regreso. Destino no se toco: sin saldoDestino. */
	public static EventoTransferencia revertida(UUID eventoId, Long transferenciaId, String clabeOrigen, String clabeDestino,
												BigDecimal monto, BigDecimal saldoOrigenTrasDevolucion, Instant fecha) {
		return new EventoTransferencia(eventoId, REVERTIDA, transferenciaId, clabeOrigen, clabeDestino, monto,
			saldoOrigenTrasDevolucion, null, fecha);
	}

	/** La routing key del contrato: transferencia.realizada o transferencia.revertida. */
	public String routingKey() {
		return REVERTIDA.equals(tipo) ? "transferencia.revertida" : "transferencia.realizada";
	}
}
