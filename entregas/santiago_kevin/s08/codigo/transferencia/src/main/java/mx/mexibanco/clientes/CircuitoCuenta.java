package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Circuit breaker manual compartido por las llamadas a cuenta.
 * No reintenta operaciones.
 */
@Component
public class CircuitoCuenta {

    private static final Logger log =
        LoggerFactory.getLogger(CircuitoCuenta.class);

    private static final int LIMITE_FALLAS = 3;
    private static final long ESPERA_NANOS =
        TimeUnit.SECONDS.toNanos(30);

    private enum Estado {
        CERRADO,
        ABIERTO,
        SEMIABIERTO
    }

    private Estado estado = Estado.CERRADO;
    private int fallasConsecutivas;
    private long abiertoDesde;
    private long generacion;

    /*
     * Permite ignorar resultados de llamadas antiguas cuando
     * el circuito ya cambio de estado.
     */
    private record Permiso(long generacion) {
    }

    public static class CircuitoAbiertoException
            extends RuntimeException {

        public CircuitoAbiertoException(String mensaje) {
            super(mensaje);
        }
    }

    public CircuitoCuenta() {
        log.info("CB cuenta: estado inicial CERRADO");
    }

    public <T> T ejecutar(String operacion, Supplier<T> llamada) {
        Permiso permiso = tomarPermiso(operacion);

        log.info("CB cuenta: llamada autorizada: {}", operacion);

        /*
         * La llamada de red se ejecuta fuera del bloqueo.
         * Los demas hilos pueden consultar el circuito sin esperar
         * a que termine esta peticion.
         */
        try {
            T resultado = llamada.get();
            registrarExito(permiso);
            return resultado;
        } catch (RuntimeException error) {
            if (esRespuestaDeNegocio(error)) {
                // Cuenta respondio: un 404 o 422 no indica una caida.
                registrarExito(permiso);
            } else {
                registrarFalla(permiso, operacion);
            }

            throw error;
        }
    }

    private synchronized Permiso tomarPermiso(String operacion) {
        if (estado == Estado.ABIERTO) {
            long transcurrido = System.nanoTime() - abiertoDesde;

            if (transcurrido < ESPERA_NANOS) {
                log.warn(
                    "CB cuenta: ABIERTO; rechazo {} sin llamar a cuenta",
                    operacion
                );

                throw new CircuitoAbiertoException(
                    "Circuito de cuenta abierto; llamada no enviada"
                );
            }

            estado = Estado.SEMIABIERTO;
            generacion++;

            log.info(
                "CB cuenta: ABIERTO -> SEMIABIERTO; "
                    + "se permite una llamada de prueba"
            );

            return new Permiso(generacion);
        }

        if (estado == Estado.SEMIABIERTO) {
            log.warn(
                "CB cuenta: SEMIABIERTO; prueba en curso; "
                    + "rechazo {} sin llamar a cuenta",
                operacion
            );

            throw new CircuitoAbiertoException(
                "Circuito de cuenta en prueba; llamada no enviada"
            );
        }

        return new Permiso(generacion);
    }

    private synchronized void registrarExito(Permiso permiso) {
        if (permiso.generacion() != generacion) {
            return;
        }

        if (estado == Estado.SEMIABIERTO) {
            estado = Estado.CERRADO;
            generacion++;

            log.info(
                "CB cuenta: SEMIABIERTO -> CERRADO; cuenta respondio"
            );
        }

        if (estado == Estado.CERRADO) {
            fallasConsecutivas = 0;
        }
    }

    private synchronized void registrarFalla(
            Permiso permiso,
            String operacion) {

        if (permiso.generacion() != generacion) {
            return;
        }

        if (estado == Estado.SEMIABIERTO) {
            abrir("fallo la llamada de prueba");
            return;
        }

        if (estado == Estado.CERRADO) {
            fallasConsecutivas++;

            log.warn(
                "CB cuenta: CERRADO; falla {}/{} en {}",
                fallasConsecutivas,
                LIMITE_FALLAS,
                operacion
            );

            if (fallasConsecutivas >= LIMITE_FALLAS) {
                abrir("se alcanzo el limite de fallas");
            }
        }
    }

    // Solo se invoca desde registrarFalla(), bajo el mismo bloqueo.
    private void abrir(String motivo) {
        Estado anterior = estado;

        estado = Estado.ABIERTO;
        abiertoDesde = System.nanoTime();
        generacion++;

        log.warn(
            "CB cuenta: {} -> ABIERTO; {}; espera de 30 segundos",
            anterior,
            motivo
        );
    }

    private boolean esRespuestaDeNegocio(RuntimeException error) {
        if (error instanceof ErrorRemotoException remoto) {
            int codigo = remoto.getEstado();

            // 408 y 429 se consideran problemas de disponibilidad.
            return codigo >= 400
                && codigo < 500
                && codigo != 408
                && codigo != 429;
        }

        return false;
    }
}