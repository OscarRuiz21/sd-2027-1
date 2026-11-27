package mx.mexibanco.notificacion.mensajeria;

/** LAB S09: un mensaje que nunca se va a poder procesar (poison pill). Va a la DLQ, no se reintenta. */
public class EventoIlegibleException extends RuntimeException {

	public EventoIlegibleException(String mensaje) {
		super(mensaje);
	}

	public EventoIlegibleException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
