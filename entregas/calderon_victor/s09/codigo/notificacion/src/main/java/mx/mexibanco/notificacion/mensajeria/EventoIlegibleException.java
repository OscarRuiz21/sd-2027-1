package mx.mexibanco.notificacion.mensajeria;

/**
 * Lab S09, Parte 4: el mensaje no es un evento de transferencia que se pueda procesar (JSON roto, le
 * faltan campos o trae un tipo que no existe). Reintentarlo no lo arregla: es un mensaje envenenado.
 */
public class EventoIlegibleException extends Exception {

	public EventoIlegibleException(String mensaje) {
		super(mensaje);
	}

	public EventoIlegibleException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
