package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final CircuitBreakerCuenta breaker;
    private final long pausaEntreCargoYAbonoMs;
    private final String falloAbonoClabe;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            NotificacionCliente notificaciones,
            CircuitBreakerCuenta breaker,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
            @Value("${mexibanco.demo.fallo-abono-clabe:}") String falloAbonoClabe) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.breaker = breaker;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.falloAbonoClabe = falloAbonoClabe;
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }

        Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));
        log.info("[SAGA {}] INICIADA {} -> {} por {}", t.getId(), claveOrigen, claveDestino, monto);
        final long txId = t.getId();

        try {
            CuentaRemota origen = breaker.ejecutar(
                    () -> cuentas.cargar(claveOrigen, monto, txId + ":cargo"));
            t.setEstado(EstadoTransferencia.CARGADA);
            t = transferencias.save(t);
            log.info("[SAGA {}] CARGADA (saldo origen {})", t.getId(), origen.saldo());

            pausaDeDemo();

            if (falloAbonoClabe != null && !falloAbonoClabe.isBlank()
                    && falloAbonoClabe.equals(claveDestino)) {
                throw new ReglaDeNegocioException(
                        "Fallo de abono FORZADO para " + claveDestino + " (FALLO_ABONO_CLABE)");
            }

            CuentaRemota destino = breaker.ejecutar(
                    () -> cuentas.abonar(claveDestino, monto, txId + ":abono"));
            t.setEstado(EstadoTransferencia.ABONADA);
            t = transferencias.save(t);
            log.info("[SAGA {}] ABONADA (saldo destino {})", t.getId(), destino.saldo());

            MovimientoRemoto cargo = null;
            MovimientoRemoto abono = null;
            try {
                cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                        monto.negate(), origen.saldo(), "a " + destino.clabe());
                abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                        monto, destino.saldo(), "de " + origen.clabe());
            } catch (RuntimeException e) {
                log.warn("[SAGA {}] no se pudieron registrar movimientos: {}", t.getId(), e.getMessage());
            }

            t.setEstado(EstadoTransferencia.COMPLETADA);
            t = transferencias.save(t);
            log.info("[SAGA {}] COMPLETADA", t.getId());

            if (cargo != null) {
                try { notificaciones.notificar(cargo); } catch (RuntimeException e) {
                    log.warn("[SAGA {}] no se pudo notificar cargo: {}", t.getId(), e.getMessage());
                }
            }
            if (abono != null) {
                try { notificaciones.notificar(abono); } catch (RuntimeException e) {
                    log.warn("[SAGA {}] no se pudo notificar abono: {}", t.getId(), e.getMessage());
                }
            }

            return t;

        } catch (RuntimeException falla) {
            log.error("[SAGA {}] fallo en estado {}: {}", t.getId(), t.getEstado(), falla.getMessage());
            return compensar(t, falla);
        }
    }

    private Transferencia compensar(Transferencia t, RuntimeException causa) {
        if (t.getEstado() != EstadoTransferencia.CARGADA) {
            t.setEstado(EstadoTransferencia.FALLIDA);
            t.setMotivoFallo(causa.getMessage());
            log.info("[SAGA {}] FALLIDA (sin cargo que compensar)", t.getId());
            return transferencias.save(t);
        }

        log.warn("[SAGA {}] COMPENSANDO: devolver {} a {}", t.getId(), t.getMonto(), t.getClaveOrigen());
        try {
            CuentaRemota origenActualizado = breaker.ejecutar(
                    () -> cuentas.abonar(t.getClaveOrigen(), t.getMonto(), t.getId() + ":compensacion"));
            try {
                movimientos.registrar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                        t.getMonto(), origenActualizado.saldo(), "reembolso de " + t.getClaveDestino());
            } catch (RuntimeException e) {
                log.warn("[SAGA {}] no se pudo registrar el reembolso: {}", t.getId(), e.getMessage());
            }
            t.setEstado(EstadoTransferencia.COMPENSADA);
            t.setMotivoFallo("Compensada tras: " + causa.getMessage());
            log.info("[SAGA {}] COMPENSADA", t.getId());
            return transferencias.save(t);
        } catch (RuntimeException fallaCompensacion) {
            log.error("[SAGA {}] COMPENSACION FALLO: {}", t.getId(), fallaCompensacion.getMessage());
            t.setEstado(EstadoTransferencia.COMPENSACION_PENDIENTE);
            t.setMotivoFallo("Compensacion pendiente: " + fallaCompensacion.getMessage());
            return transferencias.save(t);
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
