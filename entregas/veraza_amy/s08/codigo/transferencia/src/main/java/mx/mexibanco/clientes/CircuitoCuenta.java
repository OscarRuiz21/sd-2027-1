package mx.mexibanco.clientes;

import java.util.function.Supplier;
import java.util.function.LongSupplier;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Un solo sondeo en semiabierto; los errores de negocio no cuentan como indisponibilidad. */
@Component
public class CircuitoCuenta {
    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }
    private Estado estado = Estado.CERRADO;
    private int fallas;
    private long abiertoDesde;
    private boolean sondeo;
    private long generacion;
    private final LongSupplier reloj;
    private final long espera;
    public CircuitoCuenta() { this(System::nanoTime, 10_000_000_000L); }
    public CircuitoCuenta(LongSupplier reloj, long espera) { this.reloj = reloj; this.espera = espera; }
    public synchronized Estado getEstado() { return estado; }
    private void cambiar(Estado nuevo) {
        LoggerFactory.getLogger(getClass()).info("Circuito cuenta: {} -> {}", estado, nuevo);
        estado = nuevo;
        generacion++;
    }
    public <T> T ejecutar(Supplier<T> llamada) {
        long permiso;
        synchronized (this) {
            if (estado == Estado.ABIERTO) {
                if (reloj.getAsLong() - abiertoDesde < espera)
                    throw new ErrorRemotoException("cuenta", 503, "Circuito ABIERTO: cuenta temporalmente indisponible");
                cambiar(Estado.SEMIABIERTO);
            }
            if (estado == Estado.SEMIABIERTO) {
                if (sondeo) throw new ErrorRemotoException("cuenta", 503, "Sondeo de cuenta en curso");
                sondeo = true;
            }
            permiso = generacion;
        }
        try {
            T resultado = llamada.get();
            synchronized (this) { if (permiso != generacion) return resultado; fallas = 0; sondeo = false; if (estado == Estado.SEMIABIERTO) cambiar(Estado.CERRADO); }
            return resultado;
        } catch (RuntimeException error) {
            synchronized (this) {
                if (permiso != generacion) throw error;
                sondeo = false;
                boolean negocio = error instanceof ErrorRemotoException remoto && remoto.getEstado() < 500;
                if (negocio) { fallas = 0; if (estado == Estado.SEMIABIERTO) cambiar(Estado.CERRADO); }
                else if (++fallas >= 3 || estado == Estado.SEMIABIERTO) {
                    abiertoDesde = reloj.getAsLong(); cambiar(Estado.ABIERTO);
                }
            }
            throw error;
        }
    }
}
