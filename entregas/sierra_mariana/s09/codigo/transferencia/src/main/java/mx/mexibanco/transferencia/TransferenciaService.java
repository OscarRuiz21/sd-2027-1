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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSACION_PENDIENTE;
import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSADA;

@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final RabbitTemplate rabbitTemplate;
    private final long pausaEntreCargoYAbonoMs;
    private final String fallarAbonoAClabe;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
                                MovimientoCliente movimientos, NotificacionCliente notificaciones,
                                RabbitTemplate rabbitTemplate,
                                @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
                                @Value("${mexibanco.demo.fallar-abono-a-clabe:}") String fallarAbonoAClabe) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.rabbitTemplate = rabbitTemplate;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.fallarAbonoAClabe = fallarAbonoAClabe == null ? "" : fallarAbonoAClabe.trim();
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }
        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        Transferencia t = guardar(new Transferencia(claveOrigen, claveDestino, monto));

        CuentaRemota origen;
        try {
            origen = cuentas.cargar(claveOrigen, monto, t.clave("cargo"));
        } catch (RuntimeException cargoRechazado) {
            t.marcarFallida("El cargo a la CLABE " + claveOrigen + " no se hizo: " + cargoRechazado.getMessage());
            guardar(t);
            throw cargoRechazado;
        }
        t.marcarCargada(origen.saldo());
        t = guardar(t); 

        pausaDeDemo();

        CuentaRemota destino;
        try {
            destino = abonarADestino(t);
        } catch (RuntimeException abonoFallido) {
            log.warn("Transferencia {}: el abono fallo ({}); se devuelve el cargo", t.getId(), abonoFallido.getMessage());
            return compensar(t, "El abono a la CLABE " + claveDestino + " fallo: " + abonoFallido.getMessage());
        }
        t.marcarAbonada();
        t = guardar(t);

        MovimientoRemoto cargo = movimientos.registrar(t.clave("asiento-cargo"), claveOrigen,
            TipoMovimiento.TRANSFERENCIA_ENVIADA, monto.negate(), t.getSaldoOrigenTrasCargo(), "a " + claveDestino);
        MovimientoRemoto abono = movimientos.registrar(t.clave("asiento-abono"), claveDestino,
            TipoMovimiento.TRANSFERENCIA_RECIBIDA, monto, destino.saldo(), "de " + claveOrigen);
            
        // Lógica actualizada para RabbitMQ
        EventoTransferencia evento = new EventoTransferencia(
            UUID.randomUUID(),
            "TRANSFERENCIA_REALIZADA",
            t.getId(),
            claveOrigen,
            claveDestino,
            monto,
            t.getSaldoOrigenTrasCargo(),
            destino.saldo(),
            Instant.now()
        );

        rabbitTemplate.convertAndSend("transferencias", "transferencia.realizada", evento);

        t.marcarCompletada();
        return guardar(t);
    }

    private Transferencia compensar(Transferencia t, String motivo) {
        try {
            CuentaRemota origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto(), t.clave("devolucion"));
            movimientos.registrar(t.clave("asiento-cargo"), t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                t.getMonto().negate(), t.getSaldoOrigenTrasCargo(), "a " + t.getClaveDestino());
            movimientos.registrar(t.clave("asiento-devolucion"), t.getClaveOrigen(), TipoMovimiento.DEVOLUCION_TRANSFERENCIA,
                t.getMonto(), origen.saldo(), "devolucion de la transferencia " + t.getId() + ": no llego a " + t.getClaveDestino());
            t.marcarCompensada(motivo);
        } catch (RuntimeException devolucionFallida) {
            log.error("Transferencia {}: la devolucion tambien fallo ({}); queda COMPENSACION_PENDIENTE",
                t.getId(), devolucionFallida.getMessage());
            t.marcarCompensacionPendiente(motivo);
        }
        return guardar(t);
    }

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
            .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
    }

    @Transactional(readOnly = true)
    public List<Transferencia> buscarPorEstado(EstadoTransferencia estado) {
        return transferencias.findByEstado(estado);
    }

    private Transferencia guardar(Transferencia t) {
        Transferencia guardada = transferencias.save(t);
        log.info("Transferencia {}: {}", guardada.getId(), guardada.getEstado());
        return guardada;
    }

    private CuentaRemota abonarADestino(Transferencia t) {
        if (!fallarAbonoAClabe.isEmpty() && fallarAbonoAClabe.equals(t.getClaveDestino())) {
            throw new ErrorRemotoException("cuenta", 503, "Falla provocada con FALLAR_ABONO_A_CLABE=" + fallarAbonoAClabe);
        }
        return cuentas.abonar(t.getClaveDestino(), t.getMonto(), t.clave("abono"));
    }

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