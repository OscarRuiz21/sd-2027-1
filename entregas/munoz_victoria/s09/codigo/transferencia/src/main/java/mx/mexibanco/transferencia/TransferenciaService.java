package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.mensajeria.EventoTransferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSACION_PENDIENTE;
import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSADA;

@Service
public class TransferenciaService {

    private static final Logger log =
            LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final OutboxService outbox;
    private final long pausaEntreCargoYAbonoMs;
    private final String fallarAbonoAClabe;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            OutboxService outbox,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}")
            long pausaEntreCargoYAbonoMs,
            @Value("${mexibanco.demo.fallar-abono-a-clabe:}")
            String fallarAbonoAClabe) {

        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.outbox = outbox;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.fallarAbonoAClabe =
                fallarAbonoAClabe == null ? "" : fallarAbonoAClabe.trim();
    }

    public Transferencia transferir(
            String claveOrigen,
            String claveDestino,
            BigDecimal monto) {

        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException(
                    "La cuenta origen y la destino son la misma");
        }

        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        // La transferencia se guarda antes de mover el dinero.
        Transferencia t = guardar(
                new Transferencia(claveOrigen, claveDestino, monto));

        // Paso 1: retirar el dinero de la cuenta de origen.
        CuentaRemota origen;

        try {
            origen = cuentas.cargar(
                    claveOrigen, monto, t.clave("cargo"));
        } catch (RuntimeException cargoRechazado) {
            t.marcarFallida(
                    "El cargo a la CLABE " + claveOrigen
                            + " no se hizo: " + cargoRechazado.getMessage());

            guardar(t);
            throw cargoRechazado;
        }

        t.marcarCargada(origen.saldo());
        t = guardar(t);

        pausaDeDemo();

        // Paso 2: abonar el dinero a la cuenta destino.
        CuentaRemota destino;

        try {
            destino = abonarADestino(t);
        } catch (RuntimeException abonoFallido) {
            log.warn(
                    "Transferencia {}: el abono fallo ({}); se devuelve el cargo",
                    t.getId(), abonoFallido.getMessage());

            return compensar(
                    t,
                    "El abono a la CLABE " + claveDestino
                            + " fallo: " + abonoFallido.getMessage());
        }

        t.marcarAbonada();
        t = guardar(t);

        // Paso 3: registrar los movimientos.
        movimientos.registrar(
                t.clave("asiento-cargo"),
                claveOrigen,
                TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(),
                t.getSaldoOrigenTrasCargo(),
                "a " + claveDestino);

        movimientos.registrar(
                t.clave("asiento-abono"),
                claveDestino,
                TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto,
                destino.saldo(),
                "de " + claveOrigen);

        // El estado final y el evento pendiente se guardan juntos.
        t.marcarCompletada();
        t = outbox.guardarYEncolar(t, EventoTransferencia.realizada(
                t.getEventoId(),
                t.getId(),
                claveOrigen,
                claveDestino,
                monto,
                t.getSaldoOrigenTrasCargo(),
                destino.saldo(),
                t.getActualizada()));

        return t;
    }

    /**
     * Devuelve el dinero a origen cuando falla el abono.
     */
    private Transferencia compensar(Transferencia t, String motivo) {

        CuentaRemota origen;

        try {
            origen = cuentas.abonar(
                    t.getClaveOrigen(),
                    t.getMonto(),
                    t.clave("devolucion"));

            movimientos.registrar(
                    t.clave("asiento-cargo"),
                    t.getClaveOrigen(),
                    TipoMovimiento.TRANSFERENCIA_ENVIADA,
                    t.getMonto().negate(),
                    t.getSaldoOrigenTrasCargo(),
                    "a " + t.getClaveDestino());

            movimientos.registrar(
                    t.clave("asiento-devolucion"),
                    t.getClaveOrigen(),
                    TipoMovimiento.DEVOLUCION_TRANSFERENCIA,
                    t.getMonto(),
                    origen.saldo(),
                    "devolucion de la transferencia " + t.getId()
                            + ": no llego a " + t.getClaveDestino());

            t.marcarCompensada(motivo);

        } catch (RuntimeException devolucionFallida) {
            log.error(
                    "Transferencia {}: la devolucion tambien fallo ({}); queda COMPENSACION_PENDIENTE",
                    t.getId(), devolucionFallida.getMessage());

            t.marcarCompensacionPendiente(motivo);
            return guardar(t);
        }

        // El estado COMPENSADA y el evento pendiente se guardan juntos.
        t = outbox.guardarYEncolar(t, EventoTransferencia.revertida(
                t.getEventoId(),
                t.getId(),
                t.getClaveOrigen(),
                t.getClaveDestino(),
                t.getMonto(),
                origen.saldo(),
                t.getActualizada()));

        return t;
    }

    /**
     * Reintenta las compensaciones pendientes.
     */
    public int reintentarCompensacionesPendientes() {
        int terminadas = 0;

        for (Transferencia pendiente :
                transferencias.findByEstado(COMPENSACION_PENDIENTE)) {

            if (compensar(pendiente, null).getEstado() == COMPENSADA) {
                terminadas++;
            }
        }

        return terminadas;
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() ->
                        new NoEncontradoException(
                                "No existe la transferencia " + id));
    }

    @Transactional(readOnly = true)
    public List<Transferencia> buscarPorEstado(
            EstadoTransferencia estado) {
        return transferencias.findByEstado(estado);
    }

    /**
     * Guarda el estado de la transferencia y lo registra en el log.
     */
    private Transferencia guardar(Transferencia t) {
        Transferencia guardada = transferencias.save(t);

        log.info(
                "Transferencia {}: {}",
                guardada.getId(),
                guardada.getEstado());

        return guardada;
    }

    /**
     * Simula una falla del abono para probar la compensación.
     */
    private CuentaRemota abonarADestino(Transferencia t) {
        if (!fallarAbonoAClabe.isEmpty()
                && fallarAbonoAClabe.equals(t.getClaveDestino())) {

            throw new ErrorRemotoException(
                    "cuenta",
                    503,
                    "Falla provocada con FALLAR_ABONO_A_CLABE="
                            + fallarAbonoAClabe);
        }

        return cuentas.abonar(
                t.getClaveDestino(),
                t.getMonto(),
                t.clave("abono"));
    }

    /**
     * Pausa opcional para demostrar la saga durante la práctica.
     */
    private void pausaDeDemo() {
        if (pausaEntreCargoYAbonoMs <= 0) {
            return;
        }

        try {
            Thread.sleep(pausaEntreCargoYAbonoMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
