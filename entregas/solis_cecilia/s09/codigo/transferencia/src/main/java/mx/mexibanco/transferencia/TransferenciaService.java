package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.mensajeria.PublicadorDeTransferencias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSACION_PENDIENTE;
import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSADA;

/**
 * Orquestador de la saga.
 *
 * Cada paso guarda su estado en una transacción local.
 * El cargo se compensa mediante una devolución idempotente.
 *
 * S09: los avisos se publican como eventos en RabbitMQ.
 * Transferencia ya no llama por HTTP a notificación.
 */
@Service
public class TransferenciaService {

    private static final Logger log =
            LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final PublicadorDeTransferencias publicador;
    private final long pausaEntreCargoYAbonoMs;
    private final String fallarAbonoAClabe;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            PublicadorDeTransferencias publicador,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}")
            long pausaEntreCargoYAbonoMs,
            @Value("${mexibanco.demo.fallar-abono-a-clabe:}")
            String fallarAbonoAClabe) {

        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.publicador = publicador;
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

        // Validar las cuentas antes de mover dinero.
        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        // Registrar la saga antes del cargo.
        Transferencia t =
                guardar(new Transferencia(claveOrigen, claveDestino, monto));

        // Paso 1: cargo al origen.
        CuentaRemota origen;

        try {
            origen = cuentas.cargar(
                    claveOrigen,
                    monto,
                    t.clave("cargo"));

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

        // Paso 2: abono al destino.
        CuentaRemota destino;

        try {
            destino = abonarADestino(t);

        } catch (RuntimeException abonoFallido) {
            log.warn(
                    "Transferencia {}: el abono fallo ({}); se devuelve el cargo",
                    t.getId(),
                    abonoFallido.getMessage());

            return compensar(
                    t,
                    "El abono a la CLABE " + claveDestino
                            + " fallo: " + abonoFallido.getMessage());
        }

        t.marcarAbonada();
        t = guardar(t);

        // Paso 3: registrar los movimientos.
        // Después del abono no se devuelve el cargo si fallan estos registros.
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

        // LAB S09: guardar el estado final y los datos estables del evento.
        // Ya no se hacen llamadas HTTP a notificación.
        t.marcarCompletada();

        t.prepararEvento(
                "TransferenciaRealizada",
                origen.saldo(),
                destino.saldo());

        t = guardar(t);

        // Publicar después de guardar.
        // Sin Outbox existe una ventana de falla entre ambas operaciones.
        publicador.publicar(t);

        return t;
    }

    /**
     * Devuelve el cargo y registra los movimientos.
     *
     * Las claves de idempotencia se conservan durante los reintentos
     * para no devolver dinero ni registrar asientos dos veces.
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

        } catch (RuntimeException devolucionFallida) {
            log.error(
                    "Transferencia {}: la devolucion fallo ({}); "
                            + "queda COMPENSACION_PENDIENTE",
                    t.getId(),
                    devolucionFallida.getMessage());

            t.marcarCompensacionPendiente(motivo);
            return guardar(t);
        }

        t.marcarCompensada(motivo);

        // En una devolución conocemos el saldo confirmado del origen.
        // No se consulta el saldo actual del destino: se representa con null.
        t.prepararEvento(
                "TransferenciaRevertida",
                origen.saldo(),
                null);

        t = guardar(t);

        // Fuera del bloque de devolución:
        // una falla de publicación no cambia la compensación ya realizada.
        publicador.publicar(t);

        return t;
    }

    /**
     * Reintenta las compensaciones pendientes con las mismas claves.
     * También publica el evento cuando una de ellas termina compensada.
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
    public List<Transferencia> buscarPorEstado(EstadoTransferencia estado) {
        return transferencias.findByEstado(estado);
    }

    /**
     * Cada save confirma su propia transacción local.
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
     * Falla de prueba anterior al envío del abono.
     * No cuenta como falla del circuit breaker.
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
     * Pausa de prueba entre el cargo y el abono.
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