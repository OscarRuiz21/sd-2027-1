package mx.mexibanco.transferencia;

public class CircuitBreakerAbiertoException extends RuntimeException {
    public CircuitBreakerAbiertoException(String mensaje) {
        super(mensaje);
    }
}
