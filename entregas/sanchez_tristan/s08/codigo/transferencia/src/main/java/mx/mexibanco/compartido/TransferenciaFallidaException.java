package mx.mexibanco.compartido;

/**
 * Se lanza cuando la transferencia fallo y la compensacion automatica inmediata
 * TAMPOCO pudo completarse (ej. servicio caido persistente), quedando en COMPENSACION_PENDIENTE.
 */
public class TransferenciaFallidaException extends RuntimeException {

	private final Long transferenciaId;

	public TransferenciaFallidaException(Long transferenciaId, String mensaje) {
		super(mensaje);
		this.transferenciaId = transferenciaId;
	}

	public Long getTransferenciaId() {
		return transferenciaId;
	}
}
