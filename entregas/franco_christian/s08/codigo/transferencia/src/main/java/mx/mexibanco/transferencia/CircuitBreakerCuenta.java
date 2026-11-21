package mx.mexibanco.transferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

@Component
public class CircuitBreakerCuenta {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerCuenta.class);

    public enum Estado { CERRADO, ABIERTO, MEDIO_ABIERTO }

    private Estado estado = Estado.CERRADO;
    private int fallasConsecutivas = 0;
    private Instant abiertoDesde = Instant.EPOCH;

    private final int umbral;
    private final Duration espera;

    public CircuitBreakerCuenta(
            @Value("${lab.cb.umbral:3}") int umbral,
            @Value("${lab.cb.espera-segundos:15}") long esperaSegundos) {
        this.umbral = umbral;
        this.espera = Duration.ofSeconds(esperaSegundos);
    }

    public synchronized <T> T ejecutar(Supplier<T> llamada) {
        if (estado == Estado.ABIERTO) {
            if (Instant.now().isBefore(abiertoDesde.plus(espera))) {
                long restantes = Duration.between(Instant.now(), abiertoDesde.plus(espera)).toSeconds();
                log.warn("[CB] ABIERTO -> rechazo rapido (reintento en {}s)", restantes);
                throw new CircuitBreakerAbiertoException(
                        "Circuit breaker abierto: cuenta no disponible (reintento en " + restantes + "s)");
            }
            estado = Estado.MEDIO_ABIERTO;
            log.info("[CB] -> MEDIO_ABIERTO: dejo pasar 1 llamada de prueba");
        }

        try {
            T resultado = llamada.get();
            if (estado == Estado.MEDIO_ABIERTO) {
                log.info("[CB] prueba OK -> CERRADO (fallas=0)");
            }
            estado = Estado.CERRADO;
            fallasConsecutivas = 0;
            return resultado;
        } catch (RuntimeException falla) {
            if (estado == Estado.MEDIO_ABIERTO) {
                estado = Estado.ABIERTO;
                abiertoDesde = Instant.now();
                log.warn("[CB] prueba FALLO -> ABIERTO de nuevo ({})", falla.getMessage());
            } else if (estado == Estado.CERRADO) {
                fallasConsecutivas++;
                log.warn("[CB] falla {}/{} -> {}", fallasConsecutivas, umbral, falla.getMessage());
                if (fallasConsecutivas >= umbral) {
                    estado = Estado.ABIERTO;
                    abiertoDesde = Instant.now();
                    log.error("[CB] UMBRAL alcanzado -> ABIERTO por {}s", espera.toSeconds());
                }
            }
            throw falla;
        }
    }

    public synchronized Estado getEstado() { return estado; }
    public synchronized int getFallasConsecutivas() { return fallasConsecutivas; }
    public synchronized long getSegundosRestantes() {
        if (estado != Estado.ABIERTO) return 0;
        return Math.max(Duration.between(Instant.now(), abiertoDesde.plus(espera)).toSeconds(), 0);
    }
    public int getUmbral() { return umbral; }
    public long getEsperaSegundos() { return espera.toSeconds(); }
}
