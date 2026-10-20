package mx.mexibanco.clientes;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Tres fallas de infraestructura; diez segundos abierto y una sonda semiabierta. */
@Component
public class CircuitoCuenta {
    private static final Logger log=LoggerFactory.getLogger(CircuitoCuenta.class);
    private enum Estado { CERRADO, ABIERTO, SEMIABIERTO }
    private Estado estado=Estado.CERRADO;
    private int fallas;
    private long abiertoDesde,generacion;
    private boolean sonda;
    private final LongSupplier reloj;
    public CircuitoCuenta() { this(System::nanoTime); }
    CircuitoCuenta(LongSupplier reloj) { this.reloj=reloj; }
    public <T> T ejecutar(Supplier<T> operacion) {
        long turno;
        synchronized(this) {
            if(estado==Estado.ABIERTO && reloj.getAsLong()-abiertoDesde>=10_000_000_000L) cambiar(Estado.SEMIABIERTO);
            if(estado==Estado.ABIERTO || (estado==Estado.SEMIABIERTO && sonda))
                throw new ErrorRemotoException("cuenta",503,"Circuito abierto: cuenta no fue llamada");
            sonda=estado==Estado.SEMIABIERTO; turno=generacion;
        }
        try { T value=operacion.get(); resultado(turno,false); return value; }
        catch(RuntimeException e) {
            resultado(turno,!(e instanceof ErrorRemotoException remoto) || remoto.getEstado()>=500);
            throw e;
        }
    }
    private synchronized void resultado(long turno,boolean fallo) {
        if(turno!=generacion) return;
        if(!fallo) { fallas=0; if(estado==Estado.SEMIABIERTO) cambiar(Estado.CERRADO); }
        else if(estado==Estado.SEMIABIERTO || ++fallas>=3) {
            abiertoDesde=reloj.getAsLong(); cambiar(Estado.ABIERTO);
        }
        sonda=false;
    }
    private void cambiar(Estado nuevo) {
        log.warn("Circuito cuenta: {} -> {}",estado,nuevo); estado=nuevo;generacion++;
    }
    synchronized String estadoActual() { return estado.name(); }
}
