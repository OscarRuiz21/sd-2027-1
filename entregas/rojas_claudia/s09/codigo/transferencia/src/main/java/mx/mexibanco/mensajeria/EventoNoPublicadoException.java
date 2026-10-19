package mx.mexibanco.mensajeria;

/** S09: el broker no confirmo el evento (sin conexion, nack, sin respuesta o ninguna cola lo recibio). */
public class EventoNoPublicadoException extends RuntimeException {
	public EventoNoPublicadoException(String mensaje) { super(mensaje); }
	public EventoNoPublicadoException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
