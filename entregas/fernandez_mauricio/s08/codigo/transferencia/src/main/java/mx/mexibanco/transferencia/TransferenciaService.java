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
    private final String fallarAbonoClabe;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            NotificacionCliente notificaciones,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
            @Value("${mexibanco.demo.fallar-abono-clabe:}") String fallarAbonoClabe) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.fallarAbonoClabe = fallarAbonoClabe;
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }

        // Validar las cuentas antes de crear y ejecutar la operación.
        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        Transferencia transferencia = transferencias.save(
                new Transferencia(claveOrigen, claveDestino, monto));

        CuentaRemota origen;
        try {
            origen = cuentas.cargar(claveOrigen, monto);
        } catch (RuntimeException e) {
            transferencia.actualizarEstado(
                    EstadoTransferencia.INICIADA,
                    "No se confirmó el cargo en la cuenta de origen: " + mensajeDe(e));
            return transferencias.save(transferencia);
        }

        transferencia.actualizarEstado(
                EstadoTransferencia.CARGADA, "Cargo confirmado en la cuenta de origen");
        transferencia = transferencias.save(transferencia);

        pausaDeDemo();

        CuentaRemota destino;
        try {
            if (claveDestino.equals(fallarAbonoClabe)) {
                throw new ReglaDeNegocioException(
                        "Fallo de abono provocado por la configuración de demostración");
            }

            destino = cuentas.abonar(claveDestino, monto);
        } catch (RuntimeException errorAbono) {
            return compensar(transferencia, errorAbono);
        }

        transferencia.actualizarEstado(
                EstadoTransferencia.ABONADA, "Abono confirmado en la cuenta de destino");
        transferencia = transferencias.save(transferencia);

        MovimientoRemoto cargo = movimientos.registrar(
                origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(), origen.saldo(), "a " + destino.clabe());

        MovimientoRemoto abono = movimientos.registrar(
                destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, destino.saldo(), "de " + origen.clabe());

        transferencia.actualizarEstado(
                EstadoTransferencia.COMPLETADA, "Transferencia completada correctamente");
        transferencia = transferencias.save(transferencia);

        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);

        return transferencia;
    }

    public Transferencia reintentarCompensacion(Long id) {
        Transferencia transferencia = consultar(id);

        if (transferencia.getEstado() != EstadoTransferencia.COMPENSACION_PENDIENTE) {
            throw new ReglaDeNegocioException(
                    "La transferencia no tiene una compensación pendiente");
        }

        return compensar(transferencia,
                new IllegalStateException("Reintento manual de compensación"));
    }

    private Transferencia compensar(Transferencia transferencia, RuntimeException causa) {
        try {
            cuentas.compensar(
                    transferencia.getClaveOrigen(),
                    transferencia.getMonto(),
                    "COMPENSACION-TRANSFERENCIA-" + transferencia.getId());

            transferencia.actualizarEstado(
                    EstadoTransferencia.COMPENSADA,
                    "El abono falló (" + mensajeDe(causa)
                            + "). El importe fue devuelto a la cuenta de origen.");
        } catch (RuntimeException errorCompensacion) {
            transferencia.actualizarEstado(
                    EstadoTransferencia.COMPENSACION_PENDIENTE,
                    "El abono falló (" + mensajeDe(causa)
                            + ") y no se pudo confirmar la devolución ("
                            + mensajeDe(errorCompensacion)
                            + "). Se requiere reintentar la compensación.");
        }

        return transferencias.save(transferencia);
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() ->
                        new NoEncontradoException("No existe la transferencia " + id));
    }

    private String mensajeDe(Throwable error) {
        String mensaje = error.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return mensaje.length() > 250 ? mensaje.substring(0, 250) : mensaje;
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
