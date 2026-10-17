package mx.mexibanco.compartido;

/**
 * Se lanza cuando el Circuit Breaker esta en estado ABIERTO para evitar golpear
 * un servicio que esta caido o degradado (fail-fast inmediato).
 */
public class CircuitoAbiertoException extends RuntimeException {

	private final String servicio;

	public CircuitoAbiertoException(String servicio, String mensaje) {
		super(mensaje);
		this.servicio = servicio;
	}

	public String getServicio() {
		return servicio;
	}
}
