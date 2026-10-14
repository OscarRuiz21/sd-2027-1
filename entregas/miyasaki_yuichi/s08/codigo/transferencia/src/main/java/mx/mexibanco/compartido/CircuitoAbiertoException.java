package mx.mexibanco.compartido;

/** El circuito hacia otro servicio esta abierto: se rechaza sin llamarlo (HTTP 503). */
public class CircuitoAbiertoException extends RuntimeException {

	private final String servicio;

	public CircuitoAbiertoException(String servicio, long faltanMs) {
		super("Circuito abierto: el servicio " + servicio + " no esta disponible; no se le llamo"
			+ (faltanMs > 0 ? ". Se volvera a probar en " + (faltanMs / 1000 + 1) + " s" : ""));
		this.servicio = servicio;
	}

	public String getServicio() {
		return servicio;
	}
}
