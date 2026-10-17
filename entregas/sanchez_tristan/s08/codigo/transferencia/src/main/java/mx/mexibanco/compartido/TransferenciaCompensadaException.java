package mx.mexibanco.compartido;

/**
 * Se lanza cuando el abono a la cuenta destino fallo pero la compensacion
 * (devolucion del dinero a la cuenta origen) fue exitosa.
 */
public class TransferenciaCompensadaException extends RuntimeException {

	private final Long transferenciaId;

	public TransferenciaCompensadaException(Long transferenciaId, String mensaje) {
		super(mensaje);
		this.transferenciaId = transferenciaId;
	}

	public Long getTransferenciaId() {
		return transferenciaId;
	}
}
