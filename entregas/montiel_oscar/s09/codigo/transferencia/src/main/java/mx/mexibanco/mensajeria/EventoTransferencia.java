package mx.mexibanco.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen,
		String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";

	public static EventoTransferencia realizada(UUID eventoId, Long transferenciaId, String clabeOrigen,
			String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha) {
		return new EventoTransferencia(eventoId, REALIZADA, transferenciaId, clabeOrigen, clabeDestino,
			monto, saldoOrigen, saldoDestino, fecha);
	}

	public static EventoTransferencia revertida(UUID eventoId, Long transferenciaId, String clabeOrigen,
			String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen, Instant fecha) {
		return new EventoTransferencia(eventoId, REVERTIDA, transferenciaId, clabeOrigen, clabeDestino,
			monto, saldoOrigen, null, fecha);
	}

	public String routingKey() {
		return REVERTIDA.equals(tipo) ? "transferencia.revertida" : "transferencia.realizada";
	}
}
