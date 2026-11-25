package mx.mexibanco.notificacion.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventoTransferencia(
	UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen, String clabeDestino,
	BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha
) {
	public boolean realizada() {
		return "TransferenciaRealizada".equals(tipo);
	}

	boolean validoPara(String clave) {
		boolean realizadaValida = realizada() && "transferencia.realizada".equals(clave) && saldoDestino != null;
		boolean revertidaValida = "TransferenciaRevertida".equals(tipo) && "transferencia.revertida".equals(clave);
		return (realizadaValida || revertidaValida) && eventoId != null && transferenciaId != null
			&& transferenciaId > 0 && clabeValida(clabeOrigen) && clabeValida(clabeDestino)
			&& !clabeOrigen.equals(clabeDestino) && monto != null && monto.signum() > 0
			&& saldoOrigen != null && fecha != null;
	}

	private static boolean clabeValida(String clabe) {
		return clabe != null && clabe.matches("[0-9]{18}");
	}
}
