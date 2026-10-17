
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

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
                        @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}")
                        long pausaEntreCargoYAbonoMs) {
                this.transferencias = transferencias;
                this.cuentas = cuentas;
                this.movimientos = movimientos;
                this.notificaciones = notificaciones;
                this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        }

        public Transferencia transferir(
                        String claveOrigen, String claveDestino, BigDecimal monto) {

                if (claveOrigen.equals(claveDestino)) {
                        throw new ReglaDeNegocioException(
                                        "La cuenta origen y la destino son la misma");
                }

                // La Saga queda registrada antes de modificar los saldos.
                Transferencia transferencia = transferencias.save(
                                new Transferencia(claveOrigen, claveDestino, monto));

                try {
                        cuentas.consultar(claveOrigen);
                        cuentas.consultar(claveDestino);
                } catch (RuntimeException e) {
                        cambiarEstado(transferencia, EstadoTransferencia.FALLIDA,
                                        "No se pudieron validar las cuentas: " + mensaje(e));
                        throw e;
                }

                CuentaRemota origen;

                try {
                        origen = cuentas.cargar(claveOrigen, monto);
                } catch (RuntimeException e) {
                        if (rechazoDefinitivo(e)) {
                                cambiarEstado(transferencia, EstadoTransferencia.FALLIDA,
                                                "Cargo rechazado: " + mensaje(e));
                        } else {
                                cambiarEstado(transferencia,
                                                EstadoTransferencia.COMPENSACION_PENDIENTE,
                                                "No se pudo confirmar si el cargo se aplicó. "
                                                + "Requiere reconciliación: " + mensaje(e));
                        }
                        throw e;
                }

                cambiarEstado(transferencia, EstadoTransferencia.CARGADA,
                                "Cargo confirmado en la cuenta origen.");

                MovimientoRemoto cargo;

                try {
                        cargo = movimientos.registrar(
                                        origen.clabe(),
                                        TipoMovimiento.TRANSFERENCIA_ENVIADA,
                                        monto.negate(),
                                        origen.saldo(),
                                        "Transferencia " + transferencia.getId()
                                                        + " a " + claveDestino);
                } catch (RuntimeException e) {
                        compensar(transferencia, monto,
                                        "Falló el registro del cargo: " + mensaje(e));
                        throw e;
                }

                pausaDeDemo();

                CuentaRemota destino;

                try {
                        destino = cuentas.abonar(claveDestino, monto);
                } catch (RuntimeException e) {
                        if (rechazoDefinitivo(e)) {
                                compensar(transferencia, monto,
                                                "Abono rechazado: " + mensaje(e));
                        } else {
                                cambiarEstado(transferencia,
                                                EstadoTransferencia.COMPENSACION_PENDIENTE,
                                                "Resultado del abono incierto. No se devuelve "
                                                + "automáticamente para evitar un doble pago. "
                                                + mensaje(e));
                        }
                        throw e;
                }

                cambiarEstado(transferencia, EstadoTransferencia.ABONADA,
                                "Abono confirmado. Falta finalizar los registros y avisos.");

                try {
                        MovimientoRemoto abono = movimientos.registrar(
                                        destino.clabe(),
                                        TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                                        monto,
                                        destino.saldo(),
                                        "Transferencia " + transferencia.getId()
                                                        + " de " + claveOrigen);

                        notificaciones.notificar(cargo);
                        notificaciones.notificar(abono);

                        cambiarEstado(transferencia, EstadoTransferencia.COMPLETADA,
                                        "Transferencia completada correctamente.");
                } catch (RuntimeException e) {
                        // El dinero ya llegó al destino. No compensar otra vez.
                        cambiarEstado(transferencia, EstadoTransferencia.ABONADA,
                                        "El abono se confirmó, pero falló un registro "
                                        + "o una notificación. Requiere revisión: " + mensaje(e));
                }

                return transferencia;
        }

        private void compensar(
                        Transferencia transferencia, BigDecimal monto, String motivo) {

                cambiarEstado(transferencia, EstadoTransferencia.COMPENSANDO, motivo);

                CuentaRemota devuelta;

                try {
                        devuelta = cuentas.devolver(transferencia.getClaveOrigen(), monto);
                } catch (RuntimeException e) {
                        cambiarEstado(transferencia,
                                        EstadoTransferencia.COMPENSACION_PENDIENTE,
                                        motivo + ". No se pudo confirmar la devolución; "
                                        + "requiere reconciliación manual: " + mensaje(e));
                        return;
                }

                try {
                        movimientos.registrar(
                                        devuelta.clabe(),
                                        TipoMovimiento.DEVOLUCION_TRANSFERENCIA,
                                        monto,
                                        devuelta.saldo(),
                                        "Compensación de transferencia "
                                                        + transferencia.getId());

                        cambiarEstado(transferencia, EstadoTransferencia.COMPENSADA,
                                        motivo + ". Cargo devuelto y devolución registrada.");
                } catch (RuntimeException e) {
                        // La devolución de saldo respondió correctamente.
                        // No se vuelve a ejecutar para evitar devolver dos veces.
                        cambiarEstado(transferencia,
                                        EstadoTransferencia.COMPENSACION_PENDIENTE,
                                        motivo + ". Saldo devuelto, pero no se pudo confirmar "
                                        + "el registro de la devolución. Revisar el historial: "
                                        + mensaje(e));
                }
        }

        private boolean rechazoDefinitivo(RuntimeException e) {
                if (e instanceof ErrorRemotoException respuesta) {
                        return respuesta.getEstado() >= 400
                                        && respuesta.getEstado() < 500;
                }

                return e instanceof RestClientResponseException respuesta
                                && respuesta.getStatusCode().is4xxClientError();
        }

        private String mensaje(Exception e) {
                String mensaje = e.getMessage();
                return mensaje == null ? e.getClass().getSimpleName() : mensaje;
        }

        private void cambiarEstado(
                        Transferencia transferencia,
                        EstadoTransferencia estado,
                        String detalle) {
                transferencia.setEstado(estado);
                transferencia.setDetalle(detalle);
                transferencias.save(transferencia);
        }

        private void pausaDeDemo() {
                if (pausaEntreCargoYAbonoMs <= 0) {
                        return;
                }
                try {
                        Thread.sleep(pausaEntreCargoYAbonoMs);
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                }
        }

        @Transactional(readOnly = true)
        public Transferencia consultar(Long id) {
                return transferencias.findById(id)
                                .orElseThrow(() -> new NoEncontradoException(
                                                "No existe la transferencia " + id));
        }
}
