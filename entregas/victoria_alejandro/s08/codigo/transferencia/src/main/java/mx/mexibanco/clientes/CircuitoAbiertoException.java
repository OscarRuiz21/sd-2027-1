package mx.mexibanco.clientes;

import java.time.Duration;

/** El circuito hacia un servicio esta abierto: la llamada se rechazo sin hacerla. Se responde 503. */
public class CircuitoAbiertoException extends RuntimeException {

	private final String servicio;
	private final Duration reintentarEn;

	public CircuitoAbiertoException(String servicio, Duration reintentarEn) {
		super("El servicio " + servicio + " no esta disponible (circuito abierto); intente de nuevo en "
			+ Math.max(1, (reintentarEn.toMillis() + 999) / 1000) + " s");
		this.servicio = servicio;
		this.reintentarEn = reintentarEn;
	}

	public String getServicio() {
		return servicio;
	}

	public Duration getReintentarEn() {
		return reintentarEn;
	}
}
