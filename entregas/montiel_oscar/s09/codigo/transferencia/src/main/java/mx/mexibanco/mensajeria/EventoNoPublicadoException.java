package mx.mexibanco.mensajeria;

public class EventoNoPublicadoException extends RuntimeException {
	public EventoNoPublicadoException(String mensaje) { super(mensaje); }
	public EventoNoPublicadoException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
