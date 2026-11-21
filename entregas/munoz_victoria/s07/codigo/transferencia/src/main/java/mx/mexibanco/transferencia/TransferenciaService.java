package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferenciaService {
    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final long pausaEntreCargoYAbonoMs;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            NotificacionCliente notificaciones,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }

        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        Transferencia transferencia = transferencias.save(
                new Transferencia(claveOrigen, claveDestino, monto));

        CuentaRemota origen;
        try {
            origen = cuentas.cargar(claveOrigen, monto);
        } catch (RuntimeException errorCargo) {
            transferencia.setEstado(EstadoTransferencia.FALLIDA);
            transferencias.save(transferencia);
            throw errorCargo;
        }

        pausaDeDemo();

        CuentaRemota destino;
        try {
            destino = cuentas.abonar(claveDestino, monto);
        } catch (RuntimeException errorAbono) {
            try {
                CuentaRemota reembolso = cuentas.abonar(claveOrigen, monto);
                movimientos.registrar(
                        reembolso.clabe(),
                        TipoMovimiento.TRANSFERENCIA_REEMBOLSADA,
                        monto,
                        reembolso.saldo(),
                        "Reembolso de transferencia " + transferencia.getId()
                                + " cuyo destino era " + claveDestino);
                transferencia.setEstado(EstadoTransferencia.COMPENSADA);
            } catch (RuntimeException errorCompensacion) {
                transferencia.setEstado(EstadoTransferencia.COMPENSACION_PENDIENTE);
            }
            return transferencias.save(transferencia);
        }

        MovimientoRemoto cargo = movimientos.registrar(
                origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(), origen.saldo(),
                "a " + destino.clabe() + " (transferencia " + transferencia.getId());

        MovimientoRemoto abono = movimientos.registrar(
                destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, destino.saldo(),
                "de " + origen.clabe() + " (transferencia " + transferencia.getId());

        transferencia.setEstado(EstadoTransferencia.COMPLETADA);
        transferencia = transferencias.save(transferencia);

        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);
        return transferencia;
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() -> new NoEncontradoException(
                        "No existe la transferencia " + id));
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
