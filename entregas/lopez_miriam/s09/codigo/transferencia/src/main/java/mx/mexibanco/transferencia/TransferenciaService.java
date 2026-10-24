package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;

// LAB S09: mensajeria con RabbitMQ
import mx.mexibanco.mensajeria.EventoTransferencia;
import mx.mexibanco.mensajeria.PublicadorDeEventos;

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
 * Servicio que organiza las transferencias internas de Mexi Banco.
 *
 * Desde S08 utiliza una saga para registrar cada paso y compensar
 * el cargo cuando falla el abono.
 *
 * LAB S09:
 * Las notificaciones dejan de enviarse mediante HTTP sincrono.
 * Ahora se publica un evento en RabbitMQ cuando una transferencia
 * termina o cuando su compensacion se completa.
 */
@Service
public class TransferenciaService {

    private static final Logger log =
            LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;

    // LAB S09: reemplaza a NotificacionCliente
    private final PublicadorDeEventos eventos;

    private final long pausaEntreCargoYAbonoMs;
    private final String fallarAbonoAClabe;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            PublicadorDeEventos eventos,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}")
            long pausaEntreCargoYAbonoMs,
            @Value("${mexibanco.demo.fallar-abono-a-clabe:}")
            String fallarAbonoAClabe) {

        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.eventos = eventos;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.fallarAbonoAClabe =
                fallarAbonoAClabe == null ? "" : fallarAbonoAClabe.trim();
    }

    /**
     * Ejecuta una transferencia mediante la saga.
     *
     * Si el abono falla, intenta devolver el cargo.
     * Si la transferencia se completa, publica un evento
     * sin depender de que notificacion este funcionando.
     */
    public Transferencia transferir(
            String claveOrigen,
            String claveDestino,
            BigDecimal monto) {

        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException(
                    "La cuenta origen y la destino son la misma");
        }

        // Comprobar que las dos cuentas existan.
        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        // Registrar la saga antes de mover el dinero.
        Transferencia t = guardar(
                new Transferencia(claveOrigen, claveDestino, monto));

        // Paso 1: realizar el cargo.
        CuentaRemota origen;

        try {
            origen = cuentas.cargar(
                    claveOrigen,
                    monto,
                    t.clave("cargo"));

        } catch (RuntimeException cargoRechazado) {

            t.marcarFallida(
                    "El cargo a la CLABE " + claveOrigen
                            + " no se hizo: "
                            + cargoRechazado.getMessage());

            guardar(t);
            throw cargoRechazado;
        }

        t.marcarCargada(origen.saldo());
        t = guardar(t);

        pausaDeDemo();

        // Paso 2: realizar el abono.
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

        // Paso 3: registrar los movimientos del ledger.
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

        /*
         * LAB S09:
         * Antes se llamaba dos veces a notificacion mediante HTTP.
         *
         * Ahora primero terminamos la saga y guardamos el estado
         * COMPLETADA, para despues publicar el evento.
         */
        t.marcarCompletada();
        t = guardar(t);

        // Publicar el evento de transferencia realizada.
        avisar(EventoTransferencia.realizada(
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
     * Compensa una transferencia devolviendo el dinero a origen.
     *
     * LAB S09:
     * Cuando la compensacion termina, tambien se publica un evento
     * para que el cliente reciba el aviso de devolucion.
     */
    private Transferencia compensar(
            Transferencia t,
            String motivo) {

        CuentaRemota origen;

        try {
            // Devolver el dinero a la cuenta de origen.
            origen = cuentas.abonar(
                    t.getClaveOrigen(),
                    t.getMonto(),
                    t.clave("devolucion"));

            // Registrar el cargo original.
            movimientos.registrar(
                    t.clave("asiento-cargo"),
                    t.getClaveOrigen(),
                    TipoMovimiento.TRANSFERENCIA_ENVIADA,
                    t.getMonto().negate(),
                    t.getSaldoOrigenTrasCargo(),
                    "a " + t.getClaveDestino());

            // Registrar la devolucion.
            movimientos.registrar(
                    t.clave("asiento-devolucion"),
                    t.getClaveOrigen(),
                    TipoMovimiento.DEVOLUCION_TRANSFERENCIA,
                    t.getMonto(),
                    origen.saldo(),
                    "devolucion de la transferencia "
                            + t.getId()
                            + ": no llego a "
                            + t.getClaveDestino());

            t.marcarCompensada(motivo);

        } catch (RuntimeException devolucionFallida) {

            log.error(
                    "Transferencia {}: la devolucion tambien fallo ({}); queda COMPENSACION_PENDIENTE",
                    t.getId(),
                    devolucionFallida.getMessage());

            t.marcarCompensacionPendiente(motivo);

            // No publicamos porque la devolucion no ha terminado.
            return guardar(t);
        }

        // Guardar primero el estado COMPENSADA.
        t = guardar(t);

        // LAB S09: publicar el evento de devolucion.
        avisar(EventoTransferencia.revertida(
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
     * LAB S09: publica el evento en RabbitMQ.
     *
     * Si ocurre un problema con el broker, se registra el error,
     * pero no se revierte una transferencia que ya movio dinero.
     */
    private void avisar(EventoTransferencia evento) {

        try {
            eventos.publicar(evento);

            log.info(
                    "Transferencia {}: evento {} publicado ({})",
                    evento.transferenciaId(),
                    evento.tipo(),
                    evento.eventoId());

        } catch (RuntimeException sinPublicar) {

            log.error(
                    "Transferencia {}: quedo terminada pero el evento {} {} NO se publico: {}",
                    evento.transferenciaId(),
                    evento.tipo(),
                    evento.eventoId(),
                    sinPublicar.getMessage());
        }
    }

    /**
     * Reintenta las compensaciones pendientes.
     */
    public int reintentarCompensacionesPendientes() {

        int terminadas = 0;

        for (Transferencia pendiente :
                transferencias.findByEstado(COMPENSACION_PENDIENTE)) {

            if (compensar(pendiente, null).getEstado()
                    == COMPENSADA) {

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
     * Guarda el estado de la transferencia.
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
     * Provoca una falla de abono para las pruebas de compensacion.
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
     * Pausa utilizada en las demostraciones de la saga.
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
