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
    private final String fallarAbonoHacia;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
                                MovimientoCliente movimientos, NotificacionCliente notificaciones,
                                @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
                                @Value("${mexibanco.demo.fallar-abono-hacia:}") String fallarAbonoHacia) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.fallarAbonoHacia = fallarAbonoHacia;
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }
        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

       
        Transferencia transferencia = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));

       
        CuentaRemota origen;
        try {
            origen = cuentas.cargar(claveOrigen, monto);
        } catch (RuntimeException falloCargo) {
            
            transferencia.setEstado(EstadoTransferencia.FALLIDA);
            transferencia.setDetalle(recortar("El cargo falló: " + falloCargo.getMessage()));
            transferencias.save(transferencia);
            throw falloCargo;
        }
        transferencia.setEstado(EstadoTransferencia.CARGADA);
        transferencias.save(transferencia);

        pausaDeDemo();

        CuentaRemota destino;
        try {
            fallaDeDemo(claveDestino);
            destino = cuentas.abonar(claveDestino, monto);
        } catch (RuntimeException falloAbono) {
            return compensar(transferencia, falloAbono);
        }
        transferencia.setEstado(EstadoTransferencia.ABONADA);
        transferencias.save(transferencia);

        // Paso 3: asientos y avisos
        MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(), origen.saldo(), "a " + destino.clabe());
        MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, destino.saldo(), "de " + origen.clabe());
        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);

        transferencia.setEstado(EstadoTransferencia.COMPLETADA);
        return transferencias.save(transferencia);
    }

    private Transferencia compensar(Transferencia transferencia, RuntimeException causa) {
        try {
            CuentaRemota devuelta = cuentas.abonar(transferencia.getClaveOrigen(), transferencia.getMonto());
            movimientos.registrar(devuelta.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                    transferencia.getMonto(), devuelta.saldo(),
                    "devolucion de la transferencia " + transferencia.getId());
            transferencia.setEstado(EstadoTransferencia.COMPENSADA);
            transferencia.setDetalle(recortar("El abono a " + transferencia.getClaveDestino()
                    + " falló (" + causa.getMessage() + "). Se devolvió el monto a la cuenta origen."));
        } catch (RuntimeException falloCompensacion) {
            transferencia.setEstado(EstadoTransferencia.COMPENSACION_PENDIENTE);
            transferencia.setDetalle(recortar("El abono falló y la devolución también ("
                    + falloCompensacion.getMessage() + "). El monto está pendiente de devolverse al origen."));
        }
        return transferencias.save(transferencia);
    }

    
    private static String recortar(String texto) {
        return texto.length() <= 300 ? texto : texto.substring(0, 300);
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
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

    
    private void fallaDeDemo(String claveDestino) {
        if (!fallarAbonoHacia.isBlank() && fallarAbonoHacia.equals(claveDestino)) {
            throw new IllegalStateException("Falla simulada del abono hacia " + claveDestino);
        }
    }
}
