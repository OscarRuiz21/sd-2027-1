package mx.mexibanco.resiliencia;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Un solo intento en semiabierto. El monitor nunca se mantiene durante la llamada HTTP. */
@Component
public class CircuitoCuenta {
    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }
    public record Cambio(Instant fecha, Estado desde, Estado hacia) {}
    public record Vista(Estado estado, int fallasConsecutivas, int umbral, long esperaMs,
                        long llamadasPermitidas, long llamadasRechazadas, List<Cambio> cambios) {}
    private static final Logger LOG = LoggerFactory.getLogger(CircuitoCuenta.class);
    private final int umbral;
    private final long esperaMs;
    private final LongSupplier reloj;
    private final ArrayDeque<Cambio> cambios = new ArrayDeque<>();
    private Estado estado = Estado.CERRADO;
    private int fallas;
    private long abiertoDesde, generacion, permitidas, rechazadas;

    @Autowired
    public CircuitoCuenta(@Value("${mexibanco.circuito.umbral:3}") int umbral,
                          @Value("${mexibanco.circuito.espera-ms:15000}") long esperaMs) {
        this(umbral, esperaMs, System::nanoTime);
    }

    CircuitoCuenta(int umbral, long esperaMs, LongSupplier reloj) {
        if (umbral < 1 || esperaMs < 1) throw new IllegalArgumentException("Configuracion de circuito invalida");
        this.umbral = umbral;
        this.esperaMs = esperaMs;
        this.reloj = reloj;
    }

    public <T> T llamar(Supplier<T> operacion) {
        long permiso = admitir();
        try {
            T resultado = operacion.get();
            terminar(permiso, false);
            return resultado;
        } catch (RuntimeException error) {
            // Un 404 o saldo insuficiente demuestra que cuenta responde; no es una caida.
            boolean infraestructura = !(error instanceof ErrorRemotoException remoto)
                || remoto.getEstado() >= 500 || remoto.getEstado() == 429;
            terminar(permiso, infraestructura);
            throw error;
        }
    }

    private synchronized long admitir() {
        if (estado == Estado.ABIERTO && reloj.getAsLong() - abiertoDesde >= esperaMs * 1_000_000L) {
            cambiar(Estado.SEMIABIERTO);
            permitidas++;
            return generacion;
        }
        if (estado != Estado.CERRADO) {
            rechazadas++;
            throw new CircuitoAbiertoException();
        }
        permitidas++;
        return generacion;
    }

    private synchronized void terminar(long permiso, boolean fallo) {
        // Una respuesta vieja no puede cerrar un circuito abierto por otra llamada.
        if (permiso != generacion) return;
        if (!fallo) {
            fallas = 0;
            if (estado == Estado.SEMIABIERTO) cambiar(Estado.CERRADO);
        } else if (++fallas >= umbral || estado == Estado.SEMIABIERTO) {
            abiertoDesde = reloj.getAsLong();
            cambiar(Estado.ABIERTO);
        }
    }

    private void cambiar(Estado siguiente) {
        cambios.addLast(new Cambio(Instant.now(), estado, siguiente));
        if (cambios.size() > 64) cambios.removeFirst();
        LOG.info("CIRCUITO cuenta {} -> {}", estado, siguiente);
        estado = siguiente;
        generacion++;
    }

    public synchronized Vista consultar() {
        return new Vista(estado, fallas, umbral, esperaMs, permitidas, rechazadas, List.copyOf(cambios));
    }
}
