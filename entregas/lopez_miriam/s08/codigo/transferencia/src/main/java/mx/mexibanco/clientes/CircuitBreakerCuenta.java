package mx.mexibanco.clientes;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

@Component
public class CircuitBreakerCuenta {

    public enum Estado {
        CERRADO, ABIERTO, SEMIABIERTO
    }

    private Estado estado = Estado.CERRADO;
    private int fallas = 0;
    private long momentoApertura = 0;

    private static final int MAX_FALLAS = 3;
    private static final long ESPERA_MS = 10000;

    public synchronized <T> T ejecutar(Supplier<T> operacion) {
        if (estado == Estado.ABIERTO) {
            if (System.currentTimeMillis() - momentoApertura < ESPERA_MS) {
                System.out.println("CIRCUIT BREAKER ABIERTO: solicitud rechazada");
                throw new IllegalStateException(
                    "Circuit Breaker abierto: cuenta no disponible"
                );
            }

            estado = Estado.SEMIABIERTO;
            System.out.println(
                "CIRCUIT BREAKER SEMIABIERTO: probando cuenta"
            );
        }

        try {
            T resultado = operacion.get();

            fallas = 0;
            if (estado != Estado.CERRADO) {
                estado = Estado.CERRADO;
                System.out.println(
                    "CIRCUIT BREAKER CERRADO: servicio recuperado"
                );
            }

            return resultado;

        } catch (ResourceAccessException error) {
            fallas++;
            System.out.println("CIRCUIT BREAKER: falla " + fallas);

            if (fallas >= MAX_FALLAS ||
                estado == Estado.SEMIABIERTO) {
                estado = Estado.ABIERTO;
                momentoApertura = System.currentTimeMillis();
                System.out.println("CIRCUIT BREAKER ABIERTO");
            }

            throw error;
        }
    }

    public synchronized Estado getEstado() {
        return estado;
    }

    public synchronized int getFallas() {
        return fallas;
    }
}