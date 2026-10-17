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
    private final MiCircuitBreaker cbCuenta = new MiCircuitBreaker();

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
                                MovimientoCliente movimientos, NotificacionCliente notificaciones,
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

        Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
        transferencia.setEstado(EstadoTransferencia.INICIADA);
        transferencia = transferencias.save(transferencia);

        try {
            cuentas.consultar(claveOrigen); // Solo verificamos que exista la de origen

            CuentaRemota origen = cuentas.cargar(claveOrigen, monto);
            transferencia.setEstado(EstadoTransferencia.CARGADA);
            transferencia = transferencias.save(transferencia);

            pausaDeDemo();

            // TRAMPA PARA EL LAB: Forzamos el fallo del abono aquí
            if (claveDestino.equals("S08999999")) {
                throw new RuntimeException("Fallo simulado en el abono");
            }

            if (!cbCuenta.permitePaso()) {
                throw new RuntimeException("Circuit Breaker ABIERTO: cuenta no disponible");
            }
            
            CuentaRemota destino = cuentas.abonar(claveDestino, monto);
            cbCuenta.registrarExito(); 
            
            transferencia.setEstado(EstadoTransferencia.ABONADA);
            transferencia = transferencias.save(transferencia);

            MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(), origen.saldo(), "a " + destino.clabe());
            MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, destino.saldo(), "de " + origen.clabe());

            transferencia.setEstado(EstadoTransferencia.COMPLETADA);
            transferencia = transferencias.save(transferencia);

            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);
            
            return transferencia;

        } catch (Exception e) {
            cbCuenta.registrarFallo();
            
            if (transferencia.getEstado() == EstadoTransferencia.CARGADA || 
                transferencia.getEstado() == EstadoTransferencia.ABONADA) {
                try {
                    System.out.println("🔄 Iniciando compensación: devolviendo dinero a " + claveOrigen);
                    cuentas.abonar(claveOrigen, monto); 
                    transferencia.setEstado(EstadoTransferencia.COMPENSADA);
                    System.out.println("✅ Compensación exitosa.");
                } catch (Exception exCompensacion) {
                    transferencia.setEstado(EstadoTransferencia.COMPENSACION_PENDIENTE);
                    System.out.println("❌ Falló la compensación, requiere reintento manual.");
                }
            } else {
                transferencia.setEstado(EstadoTransferencia.FALLIDA);
            }
            return transferencias.save(transferencia);
        }
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
            .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
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