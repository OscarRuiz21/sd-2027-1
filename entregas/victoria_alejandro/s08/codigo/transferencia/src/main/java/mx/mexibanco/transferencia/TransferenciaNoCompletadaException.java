package mx.mexibanco.transferencia;

/**
 * El cargo se hizo pero el abono no. Lleva la transferencia para que la respuesta diga en que
 * estado quedo: COMPENSADA (se devolvio el dinero) o COMPENSACION_PENDIENTE (se devolvera).
 */
public class TransferenciaNoCompletadaException extends RuntimeException {

	private final Transferencia transferencia;

	public TransferenciaNoCompletadaException(Transferencia t) {
		super(describir(t));
		this.transferencia = t;
	}

	public Transferencia getTransferencia() {
		return transferencia;
	}

	private static String describir(Transferencia t) {
		String inicio = "Se cargaron " + t.getMonto() + " a la CLABE " + t.getClaveOrigen()
			+ ", pero el abono a " + t.getClaveDestino() + " fallo. ";
		if (t.getEstado() == EstadoTransferencia.COMPENSADA) {
			return inicio + "El monto se devolvio a la cuenta de origen; la transferencia no se realizo.";
		}
		return inicio + "La devolucion a la cuenta de origen tampoco se pudo hacer todavia; se reintentara "
			+ "automaticamente. Consulte el estado en /api/transferencias/" + t.getId() + ".";
	}
}
