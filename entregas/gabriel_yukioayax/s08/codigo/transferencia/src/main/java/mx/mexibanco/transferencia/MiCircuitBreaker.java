package mx.mexibanco.transferencia;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Circuit breaker hecho a mano para las llamadas de transferencia a cuenta.
 *
 * CERRADO     -> las llamadas pasan; cada falla suma 1. Al llegar a UMBRAL fallas seguidas se ABRE.
 * ABIERTO     -> no se llama a cuenta: se rechaza al instante con CircuitoAbiertoException.
 *                Cuando pasan ESPERA_MS desde que se abrio, la siguiente llamada lo pone SEMIABIERTO.
 * SEMIABIERTO -> deja pasar UNA llamada de prueba (las demas se rechazan mientras tanto).
 *                Si sale bien se CIERRA; si falla se vuelve a ABRIR y empieza otra espera.
 *
 * Solo cuentan como falla las que dicen que cuenta no esta sana: no contesto, no hay instancias en el
 * directorio o contesto 5xx. Un 404 o un 422 (cuenta que no existe, saldo insuficiente) es una respuesta valida.
 */
public class MiCircuitBreaker {

    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    /** Se lanza cuando el circuito no deja pasar la llamada. */
    public static class CircuitoAbiertoException extends RuntimeException {
        public CircuitoAbiertoException(String mensaje) { super(mensaje); }
    }

    private static final Logger log = LoggerFactory.getLogger(MiCircuitBreaker.class);

    private final String nombre;
    private final int umbral;
    private final long esperaMs;

    private Estado estado = Estado.CERRADO;
    private int fallosSeguidos = 0;
    private long abiertoDesde = 0;
    private boolean pruebaEnCurso = false;

    public MiCircuitBreaker(String nombre, int umbral, long esperaMs) {
        this.nombre = nombre;
        this.umbral = umbral;
        this.esperaMs = esperaMs;
        log.info("[CB {}] creado en CERRADO (umbral={} fallas, espera={} ms)", nombre, umbral, esperaMs);
    }

    /** Envuelve una llamada a cuenta. */
    public <T> T ejecutar(String que, Supplier<T> llamada) {
        antesDeLlamar(que);
        try {
            T resultado = llamada.get();
            registrarExito(que);
            return resultado;
        } catch (RuntimeException e) {
            if (cuentaComoFalla(e)) {
                registrarFallo(que, e);
            } else {
                registrarExito(que); // cuenta contesto, aunque sea con un 4xx: esta viva
            }
            throw e;
        }
    }

    public synchronized Estado getEstado() {
        return estado;
    }

    private synchronized void antesDeLlamar(String que) {
        if (estado == Estado.ABIERTO) {
            long transcurrido = System.currentTimeMillis() - abiertoDesde;
            if (transcurrido < esperaMs) {
                log.warn("[CB {}] ABIERTO: rechazo '{}' sin llamar a cuenta (faltan {} ms para probar)",
                    nombre, que, esperaMs - transcurrido);
                throw new CircuitoAbiertoException("Circuito ABIERTO hacia cuenta: no se intento '" + que + "'");
            }
            cambiarA(Estado.SEMIABIERTO, "pasaron " + transcurrido + " ms desde que se abrio");
        }
        if (estado == Estado.SEMIABIERTO) {
            if (pruebaEnCurso) {
                log.warn("[CB {}] SEMIABIERTO: ya hay una llamada de prueba, rechazo '{}'", nombre, que);
                throw new CircuitoAbiertoException("Circuito SEMIABIERTO hacia cuenta: ya hay una prueba en curso");
            }
            pruebaEnCurso = true;
            log.info("[CB {}] SEMIABIERTO: dejo pasar '{}' como llamada de prueba", nombre, que);
        }
    }

    private synchronized void registrarExito(String que) {
        pruebaEnCurso = false;
        fallosSeguidos = 0;
        if (estado != Estado.CERRADO) {
            cambiarA(Estado.CERRADO, "la llamada de prueba '" + que + "' salio bien");
        }
    }

    private synchronized void registrarFallo(String que, RuntimeException e) {
        pruebaEnCurso = false;
        fallosSeguidos++;
        if (estado == Estado.SEMIABIERTO) {
            abrir("fallo la llamada de prueba '" + que + "': " + e.getClass().getSimpleName());
        } else if (estado == Estado.CERRADO) {
            log.warn("[CB {}] CERRADO: falla {}/{} en '{}' ({})", nombre, fallosSeguidos, umbral, que, e.getClass().getSimpleName());
            if (fallosSeguidos >= umbral) {
                abrir(fallosSeguidos + " fallas seguidas");
            }
        }
    }

    private void abrir(String motivo) {
        abiertoDesde = System.currentTimeMillis();
        cambiarA(Estado.ABIERTO, motivo);
    }

    private void cambiarA(Estado nuevo, String motivo) {
        log.warn("[CB {}] {} -> {} ({})", nombre, estado, nuevo, motivo);
        estado = nuevo;
    }

    private static boolean cuentaComoFalla(RuntimeException e) {
        // cuenta contesto con 4xx: esta viva, el error es del negocio (no existe, saldo insuficiente)
        if (e instanceof ErrorRemotoException remoto) return remoto.getEstado() >= 500;
        // no contesto (ResourceAccessException), timeout, o el balanceador no tiene a quien mandar
        // la peticion ("No instances available for cuenta", IllegalStateException): falla
        return true;
    }
}
