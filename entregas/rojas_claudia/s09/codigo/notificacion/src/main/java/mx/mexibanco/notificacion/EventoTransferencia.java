package mx.mexibanco.notificacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * S09: la copia LOCAL del contrato del evento, con los mismos nombres de campo del JSON. Notificacion
 * no usa la clase de transferencia: igual que con HTTP, cada servicio tiene la suya.
 */
public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen,
								  String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen,
								  BigDecimal saldoDestino, Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";

	/**
	 * Tarea del mensaje envenenado: un JSON bien formado tambien es veneno si no cumple el contrato
	 * (sin eventoId, con un tipo que no existe, sin los datos para armar el aviso). Por mas que se
	 * reintente, nunca se va a poder procesar. Lanza IllegalArgumentException diciendo que falta.
	 */
	public void validar() {
		requerido(eventoId, "eventoId");
		requerido(tipo, "tipo");
		if (!REALIZADA.equals(tipo) && !REVERTIDA.equals(tipo)) {
			throw new IllegalArgumentException("tipo desconocido: " + tipo);
		}
		requerido(transferenciaId, "transferenciaId");
		requerido(clabeOrigen, "clabeOrigen");
		requerido(monto, "monto");
		requerido(saldoOrigen, "saldoOrigen");
		if (REALIZADA.equals(tipo)) {
			requerido(clabeDestino, "clabeDestino");
			requerido(saldoDestino, "saldoDestino");
		}
	}

	private static void requerido(Object valor, String campo) {
		if (valor == null) {
			throw new IllegalArgumentException("falta " + campo);
		}
	}
}
