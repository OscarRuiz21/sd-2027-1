package mx.mexibanco.mensajeria;

/** Lab S09: el broker no confirmo el evento (no hubo conexion, contesto nack, o ninguna cola lo recibio). */
public class EventoNoPublicadoException extends RuntimeException {

	public EventoNoPublicadoException(String mensaje) {
		super(mensaje);
	}

	public EventoNoPublicadoException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
