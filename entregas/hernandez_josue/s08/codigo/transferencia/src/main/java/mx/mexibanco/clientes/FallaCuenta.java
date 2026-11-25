package mx.mexibanco.clientes;

public class FallaCuenta extends RuntimeException {

	private final boolean resultadoIncierto;

	public FallaCuenta(String mensaje, boolean resultadoIncierto, Throwable causa) {
		super(mensaje, causa);
		this.resultadoIncierto = resultadoIncierto;
	}

	public boolean esResultadoIncierto() {
		return resultadoIncierto;
	}
}
