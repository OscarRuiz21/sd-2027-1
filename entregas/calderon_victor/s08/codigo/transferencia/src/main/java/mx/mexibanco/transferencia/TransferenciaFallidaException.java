package mx.mexibanco.transferencia;

/**
 * Lab S08: la saga no se completo. Lleva el id y el estado en que quedo la transferencia para que la
 * respuesta al cliente diga que paso y que se hizo con el dinero (ver ErroresDeLaSaga).
 */
public class TransferenciaFallidaException extends RuntimeException {

	private final int codigoHttp;
	private final Long transferencia;
	private final EstadoTransferencia estado;

	public TransferenciaFallidaException(Transferencia t, int codigoHttp, String detalle) {
		super(detalle);
		this.codigoHttp = codigoHttp;
		this.transferencia = t.getId();
		this.estado = t.getEstado();
	}

	public int getCodigoHttp() {
		return codigoHttp;
	}

	public Long getTransferencia() {
		return transferencia;
	}

	public EstadoTransferencia getEstado() {
		return estado;
	}
}
