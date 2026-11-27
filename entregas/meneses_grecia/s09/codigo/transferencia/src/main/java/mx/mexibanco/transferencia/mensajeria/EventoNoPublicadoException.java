package mx.mexibanco.transferencia.mensajeria;

/** LAB S09: el evento no llego al broker (caido, sin confirmacion o nack). */
public class EventoNoPublicadoException extends RuntimeException {

	public EventoNoPublicadoException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
