package mx.mexibanco.resiliencia;

/** El circuito no dejo pasar la llamada: se responde rapido (503) sin tocar el servicio remoto. */
public class CircuitoAbiertoException extends RuntimeException {

	private final String circuito;
	private final CircuitBreaker.Estado estado;
	private final long reintentarEnMs;

	public CircuitoAbiertoException(String circuito, CircuitBreaker.Estado estado, long reintentarEnMs, String mensaje) {
		super(mensaje);
		this.circuito = circuito;
		this.estado = estado;
		this.reintentarEnMs = reintentarEnMs;
	}

	public String getCircuito() {
		return circuito;
	}

	public CircuitBreaker.Estado getEstado() {
		return estado;
	}

	public long getReintentarEnMs() {
		return reintentarEnMs;
	}
}
