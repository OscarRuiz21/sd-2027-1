
package mx.mexibanco.clientes;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

@Component
public class CircuitBreakerManual {

    public enum Estado {
        CERRADO,
        ABIERTO,
        SEMIABIERTO
    }

    private final int limiteFallos = 3;
    private final long esperaMs = 10_000;

    private Estado estado = Estado.CERRADO;
    private int fallos = 0;
    private long aperturaMs = 0;
    private boolean pruebaEnCurso = false;

    public <T> T ejecutar(
            Callable<T> llamada,
            Predicate<Throwable> esFalloDeDisponibilidad) throws Exception {

        boolean prueba = false;

        synchronized (this) {
            if (estado == Estado.ABIERTO) {
                if (System.currentTimeMillis() - aperturaMs < esperaMs) {
                    throw new CircuitoAbiertoException(
                            "Circuit breaker ABIERTO: llamada a cuenta rechazada");
                }

                cambiarEstado(Estado.SEMIABIERTO);
            }

            if (estado == Estado.SEMIABIERTO) {
                if (pruebaEnCurso) {
                    throw new CircuitoAbiertoException(
                            "Circuit breaker SEMIABIERTO: prueba en curso");
                }

                pruebaEnCurso = true;
                prueba = true;
            }
        }

        try {
            T resultado = llamada.call();
            registrarExito();
            return resultado;
        } catch (Exception e) {
            if (esFalloDeDisponibilidad.test(e)) {
                registrarFallo(prueba);
            } else {
                // Un error funcional, como un 404 o 422, no indica
                // que el servicio esté caído.
                registrarExito();
            }

            throw e;
        }
    }

    private synchronized void registrarExito() {
        fallos = 0;
        pruebaEnCurso = false;

        if (estado == Estado.SEMIABIERTO) {
            cambiarEstado(Estado.CERRADO);
        }
    }

    private synchronized void registrarFallo(boolean prueba) {
        pruebaEnCurso = false;

        if (estado == Estado.SEMIABIERTO || prueba) {
            abrir();
            return;
        }

        if (estado == Estado.CERRADO) {
            fallos++;

            System.out.println(
                    "[CIRCUIT BREAKER] Fallos consecutivos: " + fallos);

            if (fallos >= limiteFallos) {
                abrir();
            }
        }
    }

    private void abrir() {
        aperturaMs = System.currentTimeMillis();
        fallos = 0;
        cambiarEstado(Estado.ABIERTO);
    }

    private void cambiarEstado(Estado nuevo) {
        if (estado != nuevo) {
            System.out.println(
                    "[CIRCUIT BREAKER] " + estado + " -> " + nuevo);
            estado = nuevo;
        }
    }

    public synchronized Estado getEstado() {
        return estado;
    }

    public static class CircuitoAbiertoException
            extends RuntimeException {
        public CircuitoAbiertoException(String mensaje) {
            super(mensaje);
        }
    }
}
