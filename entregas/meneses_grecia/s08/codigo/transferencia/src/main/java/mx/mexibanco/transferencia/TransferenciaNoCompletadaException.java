package mx.mexibanco.transferencia;

/**
 * S08: la saga no llego a COMPLETADA. Lleva la transferencia para que la respuesta diga que paso
 * con el dinero (devuelto, pendiente de devolver o nunca cobrado) y con que codigo HTTP.
 */
public class TransferenciaNoCompletadaException extends RuntimeException {

	private final transient Transferencia transferencia;
	private final int codigoHttp;

	public TransferenciaNoCompletadaException(Transferencia transferencia, int codigoHttp, String mensaje) {
		super(mensaje);
		this.transferencia = transferencia;
		this.codigoHttp = codigoHttp;
	}

	public Transferencia getTransferencia() {
		return transferencia;
	}

	public int getCodigoHttp() {
		return codigoHttp;
	}
}
