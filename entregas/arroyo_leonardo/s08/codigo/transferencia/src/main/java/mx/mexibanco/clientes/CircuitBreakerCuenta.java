package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Component
public class CircuitBreakerCuenta {
    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerCuenta.class);
    private final int umbral;
    private final long esperaNanos;
    private final LongSupplier reloj;
    private Estado estado = Estado.CERRADO;
    private int fallas;
    private long abiertoDesde;
    private long generacion;

    @Autowired
    public CircuitBreakerCuenta() {
        this(3, Duration.ofSeconds(15), System::nanoTime);
    }

    // Constructor accesible a pruebas del mismo paquete; no duerme el test.
    CircuitBreakerCuenta(int umbral, Duration espera, LongSupplier reloj) {
        this.umbral = umbral;
        this.esperaNanos = espera.toNanos();
        this.reloj = reloj;
    }

    public <T> T ejecutar(String operacion, Supplier<T> llamada) {
        long turno = admitir(operacion);
        try {
            T resultado = llamada.get();
            exito(turno);
            return resultado;
        } catch (RuntimeException error) {
            if (error instanceof ResourceAccessException
                    || sinInstancias(error)
                    || error instanceof ErrorRemotoException remoto && remoto.getEstado() >= 500) {
                fallo(turno);
            } else {
                // 404/422 son respuestas del servicio, no caídas de infraestructura.
                exito(turno);
            }
            throw error;
        }
    }

    public static boolean sinInstancias(RuntimeException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof IllegalStateException
                    && "No instances available for cuenta".equals(causa.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private synchronized long admitir(String operacion) {
        if (estado == Estado.ABIERTO && reloj.getAsLong() - abiertoDesde >= esperaNanos) {
            transicion(Estado.SEMIABIERTO);
            log.info("HTTP_CALL cuenta operacion={} estado={} prueba=unica", operacion, estado);
            return generacion;
        }
        if (estado != Estado.CERRADO) {
            log.info("RECHAZO_SIN_HTTP cuenta operacion={} estado={}", operacion, estado);
            throw new CircuitoAbiertoException("Circuito cuenta " + estado + "; sin llamada HTTP");
        }
        log.info("HTTP_CALL cuenta operacion={} estado={}", operacion, estado);
        return generacion;
    }

    private synchronized void exito(long turno) {
        if (turno != generacion) return; // ignora respuestas de una generación anterior
        fallas = 0;
        if (estado == Estado.SEMIABIERTO) transicion(Estado.CERRADO);
    }

    private synchronized void fallo(long turno) {
        if (turno != generacion) return;
        fallas++;
        log.info("FALLA cuenta contador={} umbral={}", fallas, umbral);
        if (estado == Estado.SEMIABIERTO || fallas >= umbral) {
            abiertoDesde = reloj.getAsLong();
            transicion(Estado.ABIERTO);
        }
    }

    private void transicion(Estado nuevo) {
        log.info("CIRCUITO cuenta {} -> {}", estado, nuevo);
        estado = nuevo;
        generacion++;
    }

    public synchronized Estado getEstado() { return estado; }
}