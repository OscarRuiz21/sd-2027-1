package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/**
 * Circuit breaker de las llamadas de transferencia a cuenta.
 */
public class CircuitoCuenta {

    private static final Logger LOG =
            LoggerFactory.getLogger(CircuitoCuenta.class);

    private static final int LIMITE_FALLAS = 3;
    private static final long ESPERA_NANOS = 15_000_000_000L;

    private enum Estado {
        CERRADO,
        ABIERTO,
        SEMIABIERTO
    }

    private Estado estado = Estado.CERRADO;
    private int fallas;
    private long apertura;
    private long generacion;

    public CircuitoCuenta() {
        LOG.info("Circuito cuenta: CERRADO");
    }

    public <T> T ejecutar(Supplier<T> llamada) {
        long permiso = permitirLlamada();

        try {
            T resultado = llamada.get();
            registrarExito(permiso);
            return resultado;
        } catch (RuntimeException error) {
            if (esFallaDeDisponibilidad(error)) {
                registrarFalla(permiso);
            } else {
                // Un rechazo de negocio no significa que cuenta esté caída.
                registrarExito(permiso);
            }
            throw error;
        }
    }

    private synchronized long permitirLlamada() {
        if (estado == Estado.ABIERTO) {
            if (System.nanoTime() - apertura < ESPERA_NANOS) {
                LOG.warn("Circuito cuenta ABIERTO: llamada bloqueada sin HTTP");
                throw new CircuitoAbiertoException(
                        "Cuenta temporalmente no disponible: circuito abierto");
            }

            cambiarEstado(Estado.SEMIABIERTO);
            LOG.info("Circuito cuenta: se permite una llamada de prueba");
            return generacion;
        }

        if (estado == Estado.SEMIABIERTO) {
            throw new CircuitoAbiertoException(
                    "Cuenta en prueba de recuperación; espera antes de reintentar");
        }

        return generacion;
    }

    private synchronized void registrarExito(long permiso) {
        if (permiso != generacion) {
            return;
        }

        fallas = 0;

        if (estado == Estado.SEMIABIERTO) {
            cambiarEstado(Estado.CERRADO);
        }
    }

    private synchronized void registrarFalla(long permiso) {
        if (permiso != generacion) {
            return;
        }

        fallas++;
        LOG.warn("Circuito cuenta: falla de disponibilidad {}", fallas);

        if (estado == Estado.SEMIABIERTO || fallas >= LIMITE_FALLAS) {
            apertura = System.nanoTime();
            cambiarEstado(Estado.ABIERTO);
        }
    }

    private void cambiarEstado(Estado nuevo) {
        LOG.info("Circuito cuenta: {} -> {}", estado, nuevo);
        estado = nuevo;
        generacion++;
    }

    private boolean esFallaDeDisponibilidad(RuntimeException error) {
        if (error instanceof ResourceAccessException) {
            return true;
        }

        if (error instanceof ErrorRemotoException remoto) {
            return remoto.getEstado() >= 500;
        }

        return error instanceof IllegalStateException
                && "No instances available for cuenta".equals(error.getMessage());
    }

    public static class CircuitoAbiertoException extends RuntimeException {

        public CircuitoAbiertoException(String mensaje) {
            super(mensaje);
        }
    }
}