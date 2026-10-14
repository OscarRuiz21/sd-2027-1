package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public class CircuitBreakerManual {

    public enum Estado {
        CERRADO,
        ABIERTO,
        SEMIABIERTO
    }

    private static final Logger log =
            LoggerFactory.getLogger(CircuitBreakerManual.class);

    private final int umbralFallos;
    private final long esperaAbiertoMs;

    private Estado estado = Estado.CERRADO;
    private int fallosConsecutivos = 0;
    private long abiertoDesde = 0;
    private boolean pruebaEnCurso = false;

    public CircuitBreakerManual(int umbralFallos, long esperaAbiertoMs) {
        this.umbralFallos = umbralFallos;
        this.esperaAbiertoMs = esperaAbiertoMs;
    }

    public <T> T ejecutar(Callable<T> operacion) {
        boolean esPrueba = prepararLlamada();

        try {
            T resultado = operacion.call();
            registrarExito(esPrueba);
            return resultado;
        } catch (Exception error) {
            registrarFallo(error, esPrueba);

            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(error);
        }
    }

    private synchronized boolean prepararLlamada() {
        if (estado == Estado.ABIERTO) {
            if (System.currentTimeMillis() - abiertoDesde < esperaAbiertoMs) {
                throw new CircuitoAbiertoException(
                        "Circuit breaker ABIERTO: llamada a cuenta rechazada");
            }

            estado = Estado.SEMIABIERTO;
            pruebaEnCurso = true;
            log.warn("Circuit breaker de cuenta: ABIERTO -> SEMIABIERTO");
            return true;
        }

        if (estado == Estado.SEMIABIERTO) {
            if (pruebaEnCurso) {
                throw new CircuitoAbiertoException(
                        "Circuit breaker SEMIABIERTO: prueba en curso");
            }

            pruebaEnCurso = true;
            return true;
        }

        return false;
    }

    private synchronized void registrarExito(boolean esPrueba) {
        fallosConsecutivos = 0;

        if (estado == Estado.SEMIABIERTO && esPrueba) {
            estado = Estado.CERRADO;
            pruebaEnCurso = false;
            log.info("Circuit breaker de cuenta: SEMIABIERTO -> CERRADO");
        }
    }

    private synchronized void registrarFallo(Exception error, boolean esPrueba) {
        if (!esFallaDeDisponibilidad(error)) {
            if (esPrueba) {
                estado = Estado.ABIERTO;
                abiertoDesde = System.currentTimeMillis();
                pruebaEnCurso = false;
                log.warn("La prueba de cuenta no confirmó recuperación; circuito ABIERTO");
            }
            return;
        }

        if (esPrueba || estado == Estado.SEMIABIERTO) {
            estado = Estado.ABIERTO;
            abiertoDesde = System.currentTimeMillis();
            fallosConsecutivos = umbralFallos;
            pruebaEnCurso = false;
            log.warn("Circuit breaker de cuenta: SEMIABIERTO -> ABIERTO");
            return;
        }

        fallosConsecutivos++;

        if (fallosConsecutivos >= umbralFallos) {
            estado = Estado.ABIERTO;
            abiertoDesde = System.currentTimeMillis();
            pruebaEnCurso = false;
            log.warn("Circuit breaker de cuenta: CERRADO -> ABIERTO");
        }
    }

    private boolean esFallaDeDisponibilidad(Exception error) {
        if (error instanceof ErrorRemotoException remoto) {
            return remoto.getEstado() >= 500;
        }

        if (error instanceof ResourceAccessException) {
            return true;
        }

        if (error instanceof IllegalStateException) {
            return error.getMessage() != null
                    && error.getMessage().contains("No instances available");
        }

        return false;
    }

    public synchronized Estado getEstado() {
        return estado;
    }

    public static class CircuitoAbiertoException extends RuntimeException {
        public CircuitoAbiertoException(String mensaje) {
            super(mensaje);
        }
    }
}