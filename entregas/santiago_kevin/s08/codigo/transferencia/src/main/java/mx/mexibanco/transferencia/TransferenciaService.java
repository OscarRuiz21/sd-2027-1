package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CircuitoCuenta;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saga orquestada con estados persistidos por separado.
 * No hay una transaccion global que abarque los servicios.
 * Las escrituras remotas no se reintentan automaticamente.
 */
@Service
public class TransferenciaService {

    private static final Logger log =
        LoggerFactory.getLogger(TransferenciaService.class);

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final long pausaEntreCargoYAbonoMs;

    @Value("${FALLAR_ABONO_CLABE:}")
    private String fallarAbonoClabe = "";

    public TransferenciaService(
            TransferenciaRepository transferencias,
            CuentaCliente cuentas,
            MovimientoCliente movimientos,
            NotificacionCliente notificaciones,
            @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}")
            long pausaEntreCargoYAbonoMs) {

        this.transferencias = transferencias;
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
                "La cuenta origen y la destino son la misma"
            );
        }

        if (monto == null || monto.signum() <= 0) {
            throw new ReglaDeNegocioException(
                "El monto debe ser mayor que cero"
            );
        }

        Transferencia transferencia = transferencias.save(
            new Transferencia(claveOrigen, claveDestino, monto)
        );

        log.info("Saga {}: INICIADA", transferencia.getId());

        // Consultar no mueve dinero.
        try {
            cuentas.consultar(claveOrigen);
            cuentas.consultar(claveDestino);
        } catch (RuntimeException error) {
            registrarError(transferencia, "consultar cuentas", error);

            guardarEstado(
                transferencia,
                Transferencia.Estado.FALLIDA,
                "No fue posible validar las cuentas. "
                    + "No se intento ningun cargo"
            );

            return transferencia;
        }

        CuentaRemota origen;

        try {
            origen = cuentas.cargar(claveOrigen, monto);
        } catch (RuntimeException error) {
            registrarError(transferencia, "cargar al origen", error);

            if (operacionNoAplicada(error)) {
                guardarEstado(
                    transferencia,
                    Transferencia.Estado.FALLIDA,
                    "El cargo fue rechazado o no pudo enviarse. "
                        + "No se realizo la transferencia"
                );
            } else {
                guardarEstado(
                    transferencia,
                    Transferencia.Estado.COMPENSACION_PENDIENTE,
                    "Resultado del cargo desconocido. Revisar si se "
                        + "aplico antes de devolver o repetir dinero"
                );
            }

            return transferencia;
        }

        guardarEstado(
            transferencia,
            Transferencia.Estado.CARGADA,
            "Cargo confirmado en la cuenta de origen"
        );

        pausaDeDemo();

        // Falla controlada antes de enviar el abono al destino.
        if (claveDestino.equals(fallarAbonoClabe)) {
            log.warn(
                "Saga {}: falla simulada de abono hacia {}",
                transferencia.getId(),
                claveDestino
            );

            return compensar(transferencia, origen);
        }

        CuentaRemota destino;

        try {
            destino = cuentas.abonar(claveDestino, monto);
        } catch (RuntimeException error) {
            registrarError(transferencia, "abonar al destino", error);

            if (operacionNoAplicada(error)) {
                return compensar(transferencia, origen);
            }

            // Un timeout no demuestra que el abono no se aplico.
            guardarEstado(
                transferencia,
                Transferencia.Estado.COMPENSACION_PENDIENTE,
                "Resultado del abono desconocido. Revisar destino "
                    + "antes de compensar para evitar duplicar dinero"
            );

            return transferencia;
        }

        guardarEstado(
            transferencia,
            Transferencia.Estado.ABONADA,
            "Abono confirmado en la cuenta de destino"
        );

        MovimientoRemoto cargo;
        MovimientoRemoto abono;

        try {
            cargo = movimientos.registrar(
                origen.clabe(),
                TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(),
                origen.saldo(),
                "Transferencia " + transferencia.getId()
                    + " a " + destino.clabe()
            );

            abono = movimientos.registrar(
                destino.clabe(),
                TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto,
                destino.saldo(),
                "Transferencia " + transferencia.getId()
                    + " de " + origen.clabe()
            );
        } catch (RuntimeException error) {
            registrarError(transferencia, "registrar movimientos", error);

            // El destino ya recibio dinero: no devolver otra vez al origen.
            guardarEstado(
                transferencia,
                Transferencia.Estado.ABONADA,
                "Dinero transferido; registro de movimientos "
                    + "incompleto. Reconciliar sin repetir cargo ni abono"
            );

            return transferencia;
        }

        guardarEstado(
            transferencia,
            Transferencia.Estado.COMPLETADA,
            "Dinero transferido y movimientos registrados; "
                + "notificaciones pendientes"
        );

        try {
            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);
        } catch (RuntimeException error) {
            registrarError(transferencia, "enviar avisos", error);

            guardarEstado(
                transferencia,
                Transferencia.Estado.COMPLETADA,
                "Transferencia completada; una o ambas notificaciones "
                    + "no se pudieron confirmar"
            );

            return transferencia;
        }

        guardarEstado(
            transferencia,
            Transferencia.Estado.COMPLETADA,
            "Transferencia completada, movimientos registrados "
                + "y avisos enviados"
        );

        return transferencia;
    }

    private Transferencia compensar(
            Transferencia transferencia,
            CuentaRemota origenTrasCargo) {

        guardarEstado(
            transferencia,
            Transferencia.Estado.COMPENSANDO,
            "El abono no se realizo. Se intentara devolver "
                + "el dinero al origen"
        );

        CuentaRemota origenRestaurado;

        try {
            origenRestaurado = cuentas.abonar(
                transferencia.getClaveOrigen(),
                transferencia.getMonto()
            );
        } catch (RuntimeException error) {
            registrarError(transferencia, "compensar", error);

            guardarEstado(
                transferencia,
                Transferencia.Estado.COMPENSACION_PENDIENTE,
                "No se pudo confirmar la devolucion. Revisar si fue "
                    + "aplicada antes de reintentar para no duplicarla"
            );

            return transferencia;
        }

        guardarEstado(
            transferencia,
            Transferencia.Estado.COMPENSADA,
            "Dinero devuelto al origen; registro de movimientos pendiente"
        );

        try {
            movimientos.registrar(
                transferencia.getClaveOrigen(),
                TipoMovimiento.TRANSFERENCIA_ENVIADA,
                transferencia.getMonto().negate(),
                origenTrasCargo.saldo(),
                "Cargo de transferencia " + transferencia.getId()
                    + " con abono fallido"
            );

            movimientos.registrar(
                transferencia.getClaveOrigen(),
                TipoMovimiento.DEPOSITO,
                transferencia.getMonto(),
                origenRestaurado.saldo(),
                "Compensacion de transferencia "
                    + transferencia.getId()
            );
        } catch (RuntimeException error) {
            registrarError(
                transferencia,
                "registrar movimientos de compensacion",
                error
            );

            guardarEstado(
                transferencia,
                Transferencia.Estado.COMPENSADA,
                "Dinero devuelto. Registro de movimientos incompleto: "
                    + "reconciliar sin repetir la devolucion"
            );

            return transferencia;
        }

        guardarEstado(
            transferencia,
            Transferencia.Estado.COMPENSADA,
            "El abono fallo; dinero devuelto al origen "
                + "y movimientos de cargo y compensacion registrados"
        );

        return transferencia;
    }

    private boolean operacionNoAplicada(RuntimeException error) {
        if (error instanceof CircuitoCuenta.CircuitoAbiertoException) {
            // El circuito rechazo la llamada antes de ejecutarla.
            return true;
        }

        if (error instanceof ErrorRemotoException remoto) {
            /*
             * Rechazos conocidos de los endpoints del laboratorio.
             * No tratamos un 408 ni un 5xx como prueba de que una
             * escritura no se aplico.
             */
            return switch (remoto.getEstado()) {
                case 400, 401, 403, 404, 405, 409, 415, 422 -> true;
                default -> false;
            };
        }

        // El balanceador no encontro destino: no envio la peticion.
        return error instanceof IllegalStateException
            && error.getMessage() != null
            && error.getMessage().startsWith(
                "No instances available for cuenta"
            );
    }

    private void guardarEstado(
            Transferencia transferencia,
            Transferencia.Estado estado,
            String detalle) {

        transferencia.actualizarEstado(estado, detalle);
        transferencias.save(transferencia);

        log.info(
            "Saga {}: {} - {}",
            transferencia.getId(),
            estado,
            detalle
        );
    }

    private void registrarError(
            Transferencia transferencia,
            String paso,
            RuntimeException error) {

        log.warn(
            "Saga {}: fallo en {}: {} - {}",
            transferencia.getId(),
            paso,
            error.getClass().getSimpleName(),
            error.getMessage()
        );
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
            .orElseThrow(() -> new NoEncontradoException(
                "No existe la transferencia " + id
            ));
    }

    private void pausaDeDemo() {
        if (pausaEntreCargoYAbonoMs <= 0) {
            return;
        }

        log.info(
            "Pausa de demo entre cargo y abono: {} ms",
            pausaEntreCargoYAbonoMs
        );

        try {
            Thread.sleep(pausaEntreCargoYAbonoMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}