package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.AbonoRechazadoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.mexibanco.clientes.CircuitoCuenta;
import java.math.BigDecimal;

@Service
public class TransferenciaService {

    private static final Logger LOG =
            LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final RegistroSaga registro;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final long pausaEntreCargoYAbonoMs;

    public TransferenciaService(
            TransferenciaRepository transferencias,
            RegistroSaga registro,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            NotificacionCliente notificaciones,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}")
            long pausaEntreCargoYAbonoMs) {

        this.transferencias = transferencias;
        this.registro = registro;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
    }

    public Transferencia transferir(
            String claveOrigen,
            String claveDestino,
            BigDecimal monto) {

        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException(
                    "La cuenta origen y la destino son la misma");
        }

        if (monto == null || monto.signum() <= 0) {
            throw new ReglaDeNegocioException(
                    "El monto debe ser positivo");
        }

        Transferencia transferencia =
                registro.iniciar(claveOrigen, claveDestino, monto);
        Long id = transferencia.getId();

        LOG.info("Saga {} INICIADA", id);

        // Estas consultas no modifican saldos.
        try {
            cuentas.consultar(claveOrigen);
            cuentas.consultar(claveDestino);
        } catch (RuntimeException error) {
            return actualizar(
                    id,
                    EstadoTransferencia.FALLIDA,
                    "No se pudieron validar las cuentas. No se solicitó el cargo. "
                            + mensaje(error));
        }

        CuentaRemota origen;

        try {
            origen = cuentas.cargar(claveOrigen, monto);
        } catch (RuntimeException error) {
            // Una respuesta perdida no permite asegurar que no hubo cargo.
            return actualizar(
                    id,
                    EstadoTransferencia.REVISION_PENDIENTE,
                    "El cargo no quedó confirmado por transferencia. "
                            + "Consultar cuenta antes de repetir o devolver dinero. "
                            + mensaje(error));
        }

        actualizar(
                id,
                EstadoTransferencia.CARGADA,
                "Cargo confirmado; pendiente de abonar al destino");

        LOG.info(
                "Saga {}: cargo confirmado. Pausa de {} ms antes del abono",
                id,
                pausaEntreCargoYAbonoMs);

        try {
            pausaDeDemo();
        } catch (RuntimeException error) {
            return actualizar(
                    id,
                    EstadoTransferencia.COMPENSACION_PENDIENTE,
                    "Se interrumpió la ejecución antes del abono. "
                            + "El cargo requiere devolución. "
                            + mensaje(error));
        }

        CuentaRemota destino;

        try {
            destino = cuentas.abonarDestino(claveDestino, monto);
        } catch (AbonoRechazadoException error) {
            // La falla de prueba ocurre antes de enviar el abono.
            return compensar(id, claveOrigen, claveDestino, monto, error);
        } catch (CircuitoCuenta.CircuitoAbiertoException error) {
            // El circuito bloqueó el abono antes de enviarlo.
            return compensar(id, claveOrigen, claveDestino, monto, error);
        } catch (IllegalStateException error) {
            if (sinInstanciasDeCuenta(error)) {
                // El balanceador no encontró un servidor al cual enviar el abono.
                return compensar(id, claveOrigen, claveDestino, monto, error);
            }

            return abonoPorRevisar(id, error);
        } catch (RuntimeException error) {
            // Ante timeout u otro resultado incierto no devolvemos a ciegas.
            return abonoPorRevisar(id, error);
        }

        actualizar(
                id,
                EstadoTransferencia.ABONADA,
                "Cargo y abono confirmados; pendientes movimientos y avisos");

        try {
            MovimientoRemoto cargo = movimientos.registrar(
                    origen.clabe(),
                    TipoMovimiento.TRANSFERENCIA_ENVIADA,
                    monto.negate(),
                    origen.saldo(),
                    "Transferencia " + id + " a " + destino.clabe());

            MovimientoRemoto abono = movimientos.registrar(
                    destino.clabe(),
                    TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                    monto,
                    destino.saldo(),
                    "Transferencia " + id + " de " + origen.clabe());

            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);
        } catch (RuntimeException error) {
            return actualizar(
                    id,
                    EstadoTransferencia.REVISION_PENDIENTE,
                    "Cargo y abono confirmados. Revisar movimientos o avisos; "
                            + "no repetir ni compensar la transferencia. "
                            + mensaje(error));
        }

        return actualizar(
                id,
                EstadoTransferencia.COMPLETADA,
                "Transferencia completada con movimientos y avisos");
    }

    private Transferencia compensar(
            Long id,
            String claveOrigen,
            String claveDestino,
            BigDecimal monto,
            RuntimeException causa) {

        // Se guarda antes de intentar la devolución.
        actualizar(
                id,
                EstadoTransferencia.COMPENSACION_PENDIENTE,
                "Abono no enviado. Pendiente devolver el cargo. "
                        + mensaje(causa));

        CuentaRemota origenDevuelto;

        try {
            origenDevuelto = cuentas.abonar(claveOrigen, monto);
        } catch (RuntimeException error) {
            return actualizar(
                    id,
                    EstadoTransferencia.COMPENSACION_PENDIENTE,
                    "No se confirmó la devolución. Revisar cuenta antes de "
                            + "reintentar: pudo aplicarse si se perdió la respuesta. "
                            + mensaje(error));
        }

        actualizar(
                id,
                EstadoTransferencia.COMPENSADA,
                "Devolución confirmada; pendiente registrar el movimiento");

        try {
            movimientos.registrar(
                    claveOrigen,
                    TipoMovimiento.DEPOSITO,
                    monto,
                    origenDevuelto.saldo(),
                    "Compensación de transferencia " + id
                            + ": abono no enviado a " + claveDestino);
        } catch (RuntimeException error) {
            return actualizar(
                    id,
                    EstadoTransferencia.REVISION_PENDIENTE,
                    "El dinero ya fue devuelto. Falta comprobar o registrar "
                            + "el movimiento de compensación. No volver a devolver. "
                            + mensaje(error));
        }

        return actualizar(
                id,
                EstadoTransferencia.COMPENSADA,
                "El abono no se envió. El cargo fue devuelto al origen "
                        + "y la devolución quedó registrada como movimiento");
    }

    private Transferencia abonoPorRevisar(Long id, RuntimeException error) {
        return actualizar(
                id,
                EstadoTransferencia.REVISION_PENDIENTE,
                "Cargo confirmado, pero resultado del abono no confirmado. "
                        + "Consultar cuenta antes de reintentar o compensar. "
                        + mensaje(error));
    }

    private Transferencia actualizar(
            Long id,
            EstadoTransferencia estado,
            String detalle) {

        String detalleLimitado = detalle.length() > 1000
                ? detalle.substring(0, 1000)
                : detalle;

        Transferencia resultado =
                registro.actualizar(id, estado, detalleLimitado);

        LOG.info("Saga {} -> {}: {}", id, estado, detalleLimitado);
        return resultado;
    }

    private boolean sinInstanciasDeCuenta(IllegalStateException error) {
        return "No instances available for cuenta".equals(error.getMessage());
    }

    private String mensaje(RuntimeException error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private void pausaDeDemo() {
        if (pausaEntreCargoYAbonoMs <= 0) {
            return;
        }

        try {
            Thread.sleep(pausaEntreCargoYAbonoMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Se interrumpió la pausa de la transferencia", error);
        }
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() ->
                        new NoEncontradoException(
                                "No existe la transferencia " + id));
    }
}