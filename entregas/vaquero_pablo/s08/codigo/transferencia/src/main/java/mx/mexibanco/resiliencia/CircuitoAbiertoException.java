package mx.mexibanco.resiliencia;

public class CircuitoAbiertoException extends RuntimeException {
    public CircuitoAbiertoException() {
        super("El circuito de cuenta esta abierto; la llamada no se envio");
    }
}
