package mx.mexibanco.transferencia.mensajeria;

import mx.mexibanco.transferencia.Transferencia;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record EventoTransferencia(
	UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen, String clabeDestino,
	BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha
) {

	public static EventoTransferencia realizada(Transferencia t, BigDecimal saldoDestino) {
		return crear(t, "TransferenciaRealizada", t.getSaldoOrigenTrasCargo(), saldoDestino);
	}

	public static EventoTransferencia revertida(Transferencia t, BigDecimal saldoDevuelto) {
		return crear(t, "TransferenciaRevertida", saldoDevuelto, null);
	}

	private static EventoTransferencia crear(Transferencia t, String tipo, BigDecimal origen, BigDecimal destino) {
		UUID id = UUID.nameUUIDFromBytes(t.clave(tipo).getBytes(StandardCharsets.UTF_8));
		return new EventoTransferencia(id, tipo, t.getId(), t.getClaveOrigen(), t.getClaveDestino(),
			t.getMonto(), origen, destino, t.getActualizada());
	}
}
