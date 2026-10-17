package mx.mexibanco.clientes;

public class CircuitoAbiertoException extends RuntimeException {
    public CircuitoAbiertoException(String mensaje) { super(mensaje); }
}