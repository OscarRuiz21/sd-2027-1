package mx.mexibanco.clientes;

/** La llamada se rechazo localmente: mientras el circuito esta abierto no se golpea a cuenta. */
public class CircuitoAbiertoException extends RuntimeException {
	public CircuitoAbiertoException() {
		super("Cuenta no disponible: circuito abierto; intenta de nuevo mas tarde");
	}
}
