package mx.mexibanco.compartido;

/** El abono fallo y el cargo se devolvio: la saga termino bien, pero la transferencia no se hizo (HTTP 409). */
public class TransferenciaCompensadaException extends RuntimeException {

	private final Long transferencia;

	public TransferenciaCompensadaException(Long transferencia, String mensaje) {
		super(mensaje);
		this.transferencia = transferencia;
	}

	public Long getTransferencia() {
		return transferencia;
	}
}
