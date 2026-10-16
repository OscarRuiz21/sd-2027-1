package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.*;
import mx.mexibanco.compartido.*;
import mx.mexibanco.resiliencia.CircuitoAbiertoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.UnknownHostException;
import static mx.mexibanco.transferencia.EstadoTransferencia.*;

/** Saga orquestada. Cada saveAndFlush termina SU transaccion antes del siguiente salto HTTP. */
@Service
public class TransferenciaService {
    private static final Logger LOG = LoggerFactory.getLogger(TransferenciaService.class);
    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final long pausaMs;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
            MovimientoCliente movimientos, NotificacionCliente notificaciones,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaMs) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaMs = pausaMs;
    }

    public Transferencia transferir(String origen, String destino, BigDecimal monto) {
        if (origen.equals(destino)) throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        if (monto == null || monto.signum() <= 0 || monto.stripTrailingZeros().scale() > 2)
            throw new ReglaDeNegocioException("El monto debe ser positivo y tener como maximo dos decimales");
        Transferencia t = transferencias.saveAndFlush(new Transferencia(origen, destino, monto));
        try {
            cuentas.consultar(origen);
            cuentas.consultar(destino);
        } catch (RuntimeException e) {
            return guardar(t, rechazo(e), "No se envio ningun cargo. " + razon(e));
        }

        t = guardar(t, CARGO_EN_PROCESO, "Se enviara el cargo al origen");
        CuentaRemota cargada;
        try {
            cargada = cuentas.cargar(origen, monto);
        } catch (RuntimeException e) {
            return guardar(t, noAplicada(e) ? rechazo(e) : REVISION_PENDIENTE,
                "Cargo sin confirmar; no repetir la solicitud. " + razon(e));
        }
        t = guardar(t, CARGADA, "Cargo confirmado; el abono todavia no ocurre");
        pausaDeDemo();
        t = guardar(t, ABONO_EN_PROCESO, "Se enviara el abono al destino");
        CuentaRemota abonada;
        try {
            abonada = cuentas.abonar(destino, monto);
        } catch (RuntimeException e) {
            if (!noAplicada(e)) {
                return guardar(t, REVISION_PENDIENTE,
                    "Cargo confirmado y abono incierto. Conciliar antes de devolver o reintentar. " + razon(e));
            }
            return compensar(t, cargada, e);
        }
        t = guardar(t, ABONADA, "Cargo y abono confirmados");
        // Los errores del ledger o de avisos no deben disparar otra devolucion de dinero.
        MovimientoRemoto cargo, abono;
        try {
            cargo = registrarCargo(t, cargada);
            abono = movimientos.registrar(destino, TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, abonada.saldo(), "saga " + t.getId() + " de " + origen);
        } catch (RuntimeException e) {
            return guardar(t, REGISTRO_PENDIENTE,
                "Dinero transferido. Conciliar movimientos; no volver a mover saldos. " + razon(e));
        }
        t = guardar(t, COMPLETADA, "Transferencia y movimientos confirmados");
        try {
            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);
        } catch (RuntimeException e) {
            t = guardar(t, COMPLETADA, "Dinero y movimientos confirmados; aviso pendiente. " + razon(e));
        }
        return t;
    }

    private Transferencia compensar(Transferencia t, CuentaRemota cargada, RuntimeException falloAbono) {
        t = guardar(t, COMPENSACION_EN_PROCESO, "Abono no aplicado. Se devolvera al origen. " + razon(falloAbono));
        CuentaRemota devuelta;
        try {
            devuelta = cuentas.abonar(t.getClaveOrigen(), t.getMonto());
        } catch (RuntimeException e) {
            return guardar(t, noAplicada(e) ? COMPENSACION_PENDIENTE : REVISION_PENDIENTE,
                "Abono destino no aplicado; devolucion sin confirmar. Conciliar antes de reintentar. " + razon(e));
        }
        t = guardar(t, COMPENSACION_APLICADA, "Dinero devuelto al origen; falta registrar movimientos");
        try {
            registrarCargo(t, cargada);
            movimientos.registrar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                t.getMonto(), devuelta.saldo(), "COMPENSACION saga " + t.getId() + ": abono destino no aplicado");
        } catch (RuntimeException e) {
            return guardar(t, REGISTRO_PENDIENTE,
                "Dinero ya devuelto. Conciliar movimientos; no repetir devolucion. " + razon(e));
        }
        return guardar(t, COMPENSADA, "El abono fallo y el dinero fue devuelto al origen; movimientos registrados");
    }

    private MovimientoRemoto registrarCargo(Transferencia t, CuentaRemota origen) {
        return movimientos.registrar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
            t.getMonto().negate(), origen.saldo(), "saga " + t.getId() + " a " + t.getClaveDestino());
    }

    private Transferencia guardar(Transferencia t, EstadoTransferencia estado, String detalle) {
        t.avanzar(estado, detalle);
        Transferencia guardada = transferencias.saveAndFlush(t);
        LOG.info("SAGA {} -> {}: {}", t.getId(), estado, detalle);
        return guardada;
    }

    public Transferencia consultar(Long id) {
        return transferencias.findById(id).orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
    }

    /** Solo fallas que prueban que cuenta no aplico la mutacion. Un timeout/5xx es ambiguo. */
    static boolean noAplicada(Throwable e) {
        if (e instanceof CircuitoAbiertoException) return true;
        if (e instanceof ErrorRemotoException remoto)
            return remoto.getEstado() == 404 || remoto.getEstado() == 409 || remoto.getEstado() == 422;
        if (e instanceof IllegalStateException && e.getMessage() != null
                && e.getMessage().startsWith("No instances available for cuenta")) return true;
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConnectException || causa instanceof UnknownHostException) return true;
        }
        return false;
    }

    private static EstadoTransferencia rechazo(RuntimeException e) {
        return e instanceof ErrorRemotoException remoto && remoto.getEstado() < 500
            && remoto.getEstado() != 429 ? RECHAZADA : NO_DISPONIBLE;
    }

    private static String razon(Exception e) {
        String texto = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return texto.substring(0, Math.min(texto.length(), 500));
    }

    private void pausaDeDemo() {
        if (pausaMs <= 0) return;
        try { Thread.sleep(pausaMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
