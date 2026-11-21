package mx.mexibanco.compartido;

/** El abono fallo y la devolucion tambien: el dinero sigue fuera del origen hasta reintentar (HTTP 503). */
public class CompensacionPendienteException extends RuntimeException {

	private final Long transferencia;

	public CompensacionPendienteException(Long transferencia, String mensaje) {
		super(mensaje);
		this.transferencia = transferencia;
	}

	public Long getTransferencia() {
		return transferencia;
	}
}
