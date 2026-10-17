package mx.mexibanco.transferencia;

import org.springframework.stereotype.Component;
import java.util.function.Supplier;

@Component
public class CircuitBreaker {
    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }
    
    private Estado estado = Estado.CERRADO;
    private int fallos = 0;
    private long tiempoApertura = 0;
    private final long ESPERA_MS = 10000;

    public synchronized <T> T proteger(Supplier<T> accion) {
        if (estado == Estado.ABIERTO) {
            if (System.currentTimeMillis() - tiempoApertura > ESPERA_MS) {
                System.out.println("Circuit Breaker: Tiempo de espera terminado. Se cambia a semiabierto");
                estado = Estado.SEMIABIERTO;
            } else {
                System.out.println("Circuit Breaker: Estado abierto. Se corta conexion para proteger el sistema");
                throw new RuntimeException("Circuit Breaker abierto. Se bloquea la llamada");
            }
        }

        try {
            T resultado = accion.get();
            if (estado == Estado.SEMIABIERTO) {
                System.out.println("Circuit Breaker: Llamada exitosa. El servicio revive y se cambia a cerrado.");
                estado = Estado.CERRADO;
                fallos = 0;
            }
            return resultado;
        } catch (Exception e) {
            fallos++;
            System.out.println("Circuit Breaker: Error de red - fallos: " + fallos);
            if (estado == Estado.SEMIABIERTO || fallos >= 2) {
                System.out.println("Circuit Breaker: limite de fallos alcanzado, se cambia a abierto.");
                estado = Estado.ABIERTO;
                tiempoApertura = System.currentTimeMillis();
            }
            throw e;
        }
    }
}