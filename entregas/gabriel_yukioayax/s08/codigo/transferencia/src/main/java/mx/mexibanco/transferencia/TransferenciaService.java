package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saga orquestada: este servicio decide que paso sigue y que hacer si uno falla.
 *
 *   INICIADA --cargo ok--> CARGADA --abono ok--> ABONADA --asientos y avisos--> COMPLETADA
 *      |                      |
 *      | cargo falla          | abono falla -> compensar (devolver el cargo al origen)
 *      v                      v
 *   FALLIDA          COMPENSADA  (o COMPENSACION_PENDIENTE si la devolucion tambien falla)
 *
 * Cada cambio de estado se guarda en la tabla transferencia, asi que si el servicio se cae a la
 * mitad, la base dice en que paso se quedo.
 */
@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final long pausaEntreCargoYAbonoMs;
    /** Para probar la compensacion con cuenta arriba: el abono hacia esta CLABE falla a proposito. */
    private final String fallarAbonoAClabe;
    private final MiCircuitBreaker cbCuenta;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
                                MovimientoCliente movimientos, NotificacionCliente notificaciones,
                                @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
                                @Value("${mexibanco.demo.fallar-abono-a-clabe:}") String fallarAbonoAClabe,
                                @Value("${mexibanco.circuit-breaker.umbral:3}") int umbral,
                                @Value("${mexibanco.circuit-breaker.espera-ms:15000}") long esperaMs) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.fallarAbonoAClabe = fallarAbonoAClabe == null ? "" : fallarAbonoAClabe.trim();
        this.cbCuenta = new MiCircuitBreaker("cuenta", umbral, esperaMs);
        if (!this.fallarAbonoAClabe.isEmpty()) {
            log.warn("FALLAR_ABONO_A_CLABE activo: todo abono hacia {} va a fallar a proposito", this.fallarAbonoAClabe);
        }
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }

        Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));
        log.info("Transferencia {}: INICIADA ({} -> {}, {})", t.getId(), claveOrigen, claveDestino, monto);

        // Paso 1: cargo. Si falla, todavia no se movio dinero: no hay nada que compensar.
        CuentaRemota origen;
        try {
            cbCuenta.ejecutar("consultar destino", () -> cuentas.consultar(claveDestino));
            origen = cbCuenta.ejecutar("cargo", () -> cuentas.cargar(claveOrigen, monto));
        } catch (ErrorRemotoException e) {
            cambiarEstado(t, EstadoTransferencia.FALLIDA);
            if (e.getEstado() < 500) throw e; // 404, 422...: cuenta contesto, se le pasa su error al cliente
            t.setDetalle("No se hizo el cargo, no se movio dinero: " + e.getMessage());
            return t;
        } catch (RuntimeException e) {
            return terminar(t, EstadoTransferencia.FALLIDA,
                "No se hizo el cargo, no se movio dinero: " + e.getMessage());
        }
        t = cambiarEstado(t, EstadoTransferencia.CARGADA);

        pausaDeDemo();

        // Paso 2: abono. Si falla, el cargo ya se aplico y hay que devolverlo.
        CuentaRemota destino;
        try {
            if (!fallarAbonoAClabe.isEmpty() && fallarAbonoAClabe.equals(claveDestino)) {
                throw new IllegalStateException("Falla provocada del abono (FALLAR_ABONO_A_CLABE=" + fallarAbonoAClabe + ")");
            }
            destino = cbCuenta.ejecutar("abono", () -> cuentas.abonar(claveDestino, monto));
        } catch (RuntimeException e) {
            log.warn("Transferencia {}: fallo el abono ({}), compenso", t.getId(), e.getMessage());
            return compensar(t, origen, e.getMessage());
        }
        t = cambiarEstado(t, EstadoTransferencia.ABONADA);

        // Paso 3: asientos y avisos. El dinero ya esta donde debe; si esto falla no se deshace,
        // la transferencia se queda en ABONADA para que se vea que faltan los asientos.
        MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
            monto.negate(), origen.saldo(), "a " + destino.clabe());
        MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
            monto, destino.saldo(), "de " + origen.clabe());
        t = cambiarEstado(t, EstadoTransferencia.COMPLETADA);

        try {
            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);
        } catch (RuntimeException e) {
            log.warn("Transferencia {}: COMPLETADA pero no salio el aviso: {}", t.getId(), e.getMessage());
        }
        t.setDetalle("Transferencia completada");
        return t;
    }

    /**
     * Compensacion del cargo: se le devuelve el monto al origen y se deja el asiento del cargo y de la
     * devolucion, para que el estado de cuenta explique por que el saldo bajo y volvio a subir.
     */
    private Transferencia compensar(Transferencia t, CuentaRemota origenTrasCargo, String motivo) {
        try {
            CuentaRemota devuelta = cbCuenta.ejecutar("compensacion", () -> cuentas.abonar(t.getClaveOrigen(), t.getMonto()));
            try {
                movimientos.registrar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                    t.getMonto().negate(), origenTrasCargo.saldo(), "a " + t.getClaveDestino() + " (transferencia " + t.getId() + ")");
                movimientos.registrar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                    t.getMonto(), devuelta.saldo(), "devolucion de la transferencia " + t.getId() + " (compensacion)");
            } catch (RuntimeException e) {
                log.warn("Transferencia {}: compensada, pero no se registraron los asientos: {}", t.getId(), e.getMessage());
            }
            log.info("Transferencia {}: dinero devuelto a {}, saldo {}", t.getId(), t.getClaveOrigen(), devuelta.saldo());
            return terminar(t, EstadoTransferencia.COMPENSADA,
                "El abono fallo (" + motivo + "); el cargo se devolvio a la cuenta de origen");
        } catch (RuntimeException e) {
            return terminar(t, EstadoTransferencia.COMPENSACION_PENDIENTE,
                "El abono fallo (" + motivo + ") y la devolucion tambien (" + e.getMessage()
                    + "); el dinero salio del origen y la devolucion queda pendiente de reintento");
        }
    }

    private Transferencia cambiarEstado(Transferencia t, EstadoTransferencia nuevo) {
        t.setEstado(nuevo);
        Transferencia guardada = transferencias.save(t);
        log.info("Transferencia {}: {}", guardada.getId(), nuevo);
        return guardada;
    }

    private Transferencia terminar(Transferencia t, EstadoTransferencia estado, String detalle) {
        Transferencia guardada = cambiarEstado(t, estado);
        guardada.setDetalle(detalle);
        return guardada;
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
            .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
    }

    public MiCircuitBreaker.Estado estadoDelCircuito() {
        return cbCuenta.getEstado();
    }

    private void pausaDeDemo() {
        if (pausaEntreCargoYAbonoMs <= 0) return;
        try {
            Thread.sleep(pausaEntreCargoYAbonoMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
