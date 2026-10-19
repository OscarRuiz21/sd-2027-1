package mx.mexibanco.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * S09: el CONTRATO con quien escuche. Es un record propio y no la entidad Transferencia: cambiar una
 * columna no debe romper a nadie afuera. Es un hecho en pasado ("ya se hizo"), no una orden.
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

	/** La routing key del contrato. */
	public String routingKey() {
		return REVERTIDA.equals(tipo) ? "transferencia.revertida" : "transferencia.realizada";
	}
}
