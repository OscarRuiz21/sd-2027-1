package mx.mexibanco.clientes;

/** Eureka no proporciono ninguna instancia; el abono no salio por HTTP y es seguro reintentar. */
public class CuentaSinInstanciasException extends RuntimeException {
	public CuentaSinInstanciasException() {
		super("No hay instancias disponibles de cuenta; no se envio la peticion HTTP");
	}
}
