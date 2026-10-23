package mx.mexibanco.compartido;

/**
 * Falla de prueba provocada antes de enviar el abono.
 */
public class AbonoRechazadoException extends RuntimeException {

    public AbonoRechazadoException(String mensaje) {
        super(mensaje);
    }
}