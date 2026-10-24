
package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
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
 * Transferencia INTERNA, entre dos cuentas del mismo banco.
 *
 * v08: este servicio es el ORQUESTADOR de una saga. Cada paso se guarda
 * por separado y tiene su compensacion correspondiente.
 *
 * S09: los avisos se publican como eventos en RabbitMQ, sin llamar
 * directamente al servicio de notificaciones.
 */
@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final PublicadorDeEventos eventos;
    private final long pausaEntreCargoYAbonoMs;
    private final String fallarAbonoAClabe;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            PublicadorDeEventos eventos,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
            @Value("${mexibanco.demo.fallar-abono-a-clabe:}") String fallarAbonoAClabe) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.eventos = eventos;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.fallarAbonoAClabe = fallarAbonoAClabe == null ? "" : fallarAbonoAClabe.trim();
    }

    /**
     * Corre la saga completa. Regresa la transferencia en COMPLETADA,
     * COMPENSADA o COMPENSACION_PENDIENTE.
     */
    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }

        // Primero se comprueba que existan las dos cuentas.
        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        // La transferencia se registra antes de mover el dinero.
        Transferencia t = guardar(new Transferencia(claveOrigen, claveDestino, monto));

        // Paso 1: cargo a la cuenta origen.
        CuentaRemota origen;
        try {
            origen = cuentas.cargar(claveOrigen, monto, t.clave("cargo"));
        } catch (RuntimeException cargoRechazado) {
            t.marcarFallida("El cargo a la CLABE " + claveOrigen
                    + " no se hizo: " + cargoRechazado.getMessage());
            guardar(t);
            throw cargoRechazado;
        }

        t.marcarCargada(origen.saldo());
        t = guardar(t);

        pausaDeDemo();

        // Paso 2: abono a la cuenta destino.
        CuentaRemota destino;
        try {
            destino = abonarADestino(t);
        } catch (RuntimeException abonoFallido) {
            log.warn("Transferencia {}: el abono fallo ({}); se devuelve el cargo",
                    t.getId(), abonoFallido.getMessage());
            return compensar(t,
                    "El abono a la CLABE " + claveDestino
                            + " fallo: " + abonoFallido.getMessage());
        }

        t.marcarAbonada();
        t = guardar(t);

        // Paso 3: registrar los asientos del cargo y del abono.
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

        // Primero se guarda COMPLETADA en la base de datos.
        t.marcarCompletada();
        t = guardar(t);

        // Despues se publica el evento TransferenciaRealizada.
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
     * Compensa el cargo cuando falla el abono a destino.
     * Si la devolucion falla, la transferencia queda pendiente
     * y el reintento programado puede volver a ejecutar este metodo.
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
                    t.getId(),
                    devolucionFallida.getMessage());

            t.marcarCompensacionPendiente(motivo);

            // No se publica un evento de reversión mientras el dinero
            // no haya regresado a la cuenta origen.
            return guardar(t);
        }

        // Se guarda COMPENSADA antes de publicar el evento.
        t = guardar(t);

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
     * Reintenta las compensaciones pendientes.
     */
    public int reintentarCompensacionesPendientes() {
        int terminadas = 0;

        for (Transferencia pendiente : transferencias.findByEstado(COMPENSACION_PENDIENTE)) {
            if (compensar(pendiente, null).getEstado() == COMPENSADA) {
                terminadas++;
            }
        }

        return terminadas;
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() -> new NoEncontradoException(
                        "No existe la transferencia " + id));
    }

    @Transactional(readOnly = true)
    public List<Transferencia> buscarPorEstado(EstadoTransferencia estado) {
        return transferencias.findByEstado(estado);
    }

    /**
     * Guarda cada paso de la saga y deja una linea en el log.
     */
    private Transferencia guardar(Transferencia t) {
        Transferencia guardada = transferencias.save(t);
        log.info("Transferencia {}: {}", guardada.getId(), guardada.getEstado());
        return guardada;
    }

    /**
     * Publica el evento sin revertir una transferencia ya terminada
     * si falla la publicacion.
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
     * Permite provocar un fallo de abono durante las pruebas de compensacion.
     */
    private CuentaRemota abonarADestino(Transferencia t) {
        if (!fallarAbonoAClabe.isEmpty()
                && fallarAbonoAClabe.equals(t.getClaveDestino())) {
            throw new ErrorRemotoException(
                    "cuenta",
                    503,
                    "Falla provocada con FALLAR_ABONO_A_CLABE=" + fallarAbonoAClabe);
        }

        return cuentas.abonar(
                t.getClaveDestino(),
                t.getMonto(),
                t.clave("abono"));
    }

    /**
     * Abre una ventana entre el cargo y el abono para las demostraciones.
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
