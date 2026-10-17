package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.*;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import static mx.mexibanco.transferencia.EstadoTransferencia.*;

@Service
public class TransferenciaService {
    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);
    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final long pausaMs;
    private final String fallarAbonoClabe;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
            MovimientoCliente movimientos, NotificacionCliente notificaciones,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaMs,
            @Value("${mexibanco.demo.fallar-abono-clabe:}") String fallarAbonoClabe) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaMs = pausaMs;
        this.fallarAbonoClabe = fallarAbonoClabe;
    }

    // No agregar @Transactional: cada transición debe confirmarse antes del siguiente HTTP.
    public Transferencia transferir(String origen, String destino, BigDecimal monto) {
        if (origen.equals(destino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaDeNegocioException("El monto debe ser positivo");
        }
        Transferencia t = transferencias.saveAndFlush(new Transferencia(origen, destino, monto));
        try {
            cuentas.consultar(origen);
            cuentas.consultar(destino);
        } catch (RuntimeException error) {
            return estado(t, FALLIDA, "No se intentó el cargo: " + mensaje(error));
        }

        estado(t, CARGO_EN_CURSO, "Se intentará el cargo al origen");
        CuentaRemota cargada;
        try {
            cargada = cuentas.cargar(origen, monto);
        } catch (RuntimeException error) {
            return estado(t, noAplicada(error) ? FALLIDA : REVISION_PENDIENTE,
                    "Cargo no confirmado; no repetir sin verificar: " + mensaje(error));
        }
        estado(t, CARGADA, "Cargo confirmado en origen");

        MovimientoRemoto cargo;
        try {
            cargo = movimientos.registrar(origen, TipoMovimiento.TRANSFERENCIA_ENVIADA,
                    monto.negate(), cargada.saldo(), "saga " + t.getId() + " a " + destino);
        } catch (RuntimeException error) {
            // Aún no se llamó al abono. La devolución se puede intentar;
            // el asiento de cargo requiere conciliación si su respuesta se perdió.
            return compensar(t, "Registro del cargo no confirmado: " + mensaje(error), false);
        }

        pausa(t);
        if (!fallarAbonoClabe.isBlank() && fallarAbonoClabe.equals(destino)) {
            return compensar(t, "Falla de abono simulada para " + destino, true);
        }

        estado(t, ABONO_EN_CURSO, "Se intentará el abono al destino");
        CuentaRemota abonada;
        try {
            abonada = cuentas.abonar(destino, monto);
        } catch (RuntimeException error) {
            if (!noAplicada(error)) {
                return estado(t, REVISION_PENDIENTE,
                        "Abono incierto: verificar destino antes de devolver: " + mensaje(error));
            }
            return compensar(t, "Abono rechazado/no enviado: " + mensaje(error), true);
        }
        estado(t, ABONADA, "Abono confirmado; no devolver dinero por fallas posteriores");

        MovimientoRemoto abono;
        try {
            abono = movimientos.registrar(destino, TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                    monto, abonada.saldo(), "saga " + t.getId() + " de " + origen);
        } catch (RuntimeException error) {
            return estado(t, REGISTRO_PENDIENTE,
                    "Dinero transferido; conciliar movimiento del destino: " + mensaje(error));
        }
        try {
            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);
        } catch (RuntimeException error) {
            return estado(t, NOTIFICACION_PENDIENTE,
                    "Saldos y movimientos completos; aviso pendiente: " + mensaje(error));
        }
        return estado(t, COMPLETADA, "Transferencia completada");
    }

    private Transferencia compensar(Transferencia t, String causa, boolean cargoRegistrado) {
        estado(t, COMPENSACION_PENDIENTE, causa + "; devolución pendiente");
        estado(t, DEVOLUCION_EN_CURSO, causa + "; se intentará devolver al origen");
        CuentaRemota devuelta;
        try {
            devuelta = cuentas.abonar(t.getClaveOrigen(), t.getMonto());
        } catch (RuntimeException error) {
            return estado(t, COMPENSACION_PENDIENTE,
                    causa + "; devolución NO confirmada; conciliar antes de reintentar: " + mensaje(error));
        }
        estado(t, DEVUELTA, causa + "; dinero devuelto, movimiento pendiente");
        try {
            movimientos.registrar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                    t.getMonto(), devuelta.saldo(), "COMPENSACION saga " + t.getId());
        } catch (RuntimeException error) {
            return estado(t, REGISTRO_PENDIENTE,
                    "DINERO YA DEVUELTO; no abonar otra vez. Conciliar movimiento: " + mensaje(error));
        }
        if (!cargoRegistrado) {
            return estado(t, REGISTRO_PENDIENTE,
                    "DINERO YA DEVUELTO y compensación registrada; conciliar asiento de cargo");
        }
        return estado(t, COMPENSADA, causa + "; monto devuelto al origen y movimiento registrado");
    }

    // Clasifica solo fallas que permiten concluir que la operación no se aplicó.
    // Un timeout de lectura o un 5xx genérico NO demuestra que no hubo commit.
    private boolean noAplicada(RuntimeException error) {
        if (error instanceof CircuitoAbiertoException || CircuitBreakerCuenta.sinInstancias(error)) return true;
        if (error instanceof ErrorRemotoException remoto) {
            return remoto.getEstado() >= 400 && remoto.getEstado() < 500;
        }
        if (error instanceof ResourceAccessException) {
            for (Throwable causa = error; causa != null; causa = causa.getCause()) {
                if (causa instanceof ConnectException || causa instanceof UnknownHostException
                        || causa instanceof NoRouteToHostException) return true;
            }
        }
        return false;
    }

    private Transferencia estado(Transferencia t, EstadoTransferencia nuevo, String detalle) {
        t.cambiarEstado(nuevo, detalle);
        transferencias.saveAndFlush(t);
        log.info("SAGA id={} estado={} detalle={}", t.getId(), nuevo, detalle);
        return t;
    }

    private String mensaje(RuntimeException error) {
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    private void pausa(Transferencia t) {
        if (pausaMs <= 0) return;
        log.info("PAUSA id={} cargo confirmado; esperar {} ms antes del abono", t.getId(), pausaMs);
        try {
            Thread.sleep(pausaMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pausa interrumpida; revisar saga " + t.getId(), error);
        }
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
    }
}