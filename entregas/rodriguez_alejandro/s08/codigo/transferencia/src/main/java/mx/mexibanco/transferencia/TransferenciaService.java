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
    private final CircuitBreaker circuitBreaker; 
    private final long pausaEntreCargoYAbonoMs;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
                                MovimientoCliente movimientos, NotificacionCliente notificaciones,
                                CircuitBreaker circuitBreaker,
                                @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.circuitBreaker = circuitBreaker;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }

        // se valida la existencia protegida por el Circuit Breaker
        circuitBreaker.proteger(() -> {
            cuentas.consultar(claveOrigen);
            cuentas.consultar(claveDestino);
            return null;
        });

        Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
        transferencia.setEstado("INICIADA");
        transferencias.save(transferencia);

        CuentaRemota origen = null;
        CuentaRemota destino = null;

        try {
            // Se protege el  cargo por el Circuit Breaker
            origen = circuitBreaker.proteger(() -> cuentas.cargar(claveOrigen, monto));
            transferencia.setEstado("CARGADA");
            transferencias.save(transferencia);

            pausaDeDemo();

            if ("S08_DESTINO_FALLO".equals(claveDestino)) {
                throw new RuntimeException("Fallo simulado en destino para probar compensacion");
            }

            // Se protege el abono por el Circuit Breaker
            destino = circuitBreaker.proteger(() -> cuentas.abonar(claveDestino, monto));
            transferencia.setEstado("COMPLETADA");
            transferencias.save(transferencia);

            MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(), origen.saldo(), "a " + destino.clabe());
            MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, destino.saldo(), "de " + origen.clabe());

            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);

        } catch (Exception e) {
            if ("CARGADA".equals(transferencia.getEstado())) {
                try {
                    // se protege la compensación por el Circuit Breaker
                    circuitBreaker.proteger(() -> { cuentas.abonar(claveOrigen, monto); return null; });
                    transferencia.setEstado("COMPENSADA");
                } catch (Exception exCompensacion) {
                    transferencia.setEstado("COMPENSACION_PENDIENTE");
                }
            } else {
                transferencia.setEstado("FALLIDA");
            }
            transferencias.save(transferencia);
            throw new ReglaDeNegocioException("Transferencia fallo. Estado: " + transferencia.getEstado() + ". Detalle: " + e.getMessage());
        }

        return transferencia;
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id).orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
    }

    private void pausaDeDemo() {
        if (pausaEntreCargoYAbonoMs <= 0) return;
        try { Thread.sleep(pausaEntreCargoYAbonoMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}