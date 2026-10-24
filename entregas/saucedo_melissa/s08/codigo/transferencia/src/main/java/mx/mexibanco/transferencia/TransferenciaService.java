package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.compartido.TransferenciaFallidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Orquestador de la saga de transferencia (S08).
 *
 * Cada paso deja su estado persistido en la tabla transferencia, de modo que siempre se puede
 * saber en que punto quedo una operacion:
 *
 *   INICIADA -> CARGADA -> COMPLETADA
 *                  |
 *                  +-> COMPENSADA              (el abono fallo y se devolvio el cargo)
 *                  +-> COMPENSACION_PENDIENTE  (el abono y la devolucion fallaron)
 *   INICIADA -> FALLIDA                         (el cargo fue rechazado; no se movio dinero)
 *
 * No hay @Transactional en transferir(): cada save() es su propia transaccion local, porque el
 * dinero vive en otro servicio y una transaccion de base de datos no puede deshacerlo.
 */
@Service
public class TransferenciaService {

        private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

        private final TransferenciaRepository transferencias;
        private final CuentaCliente cuentas;
        private final MovimientoCliente movimientos;
        private final NotificacionCliente notificaciones;
        private final long pausaEntreCargoYAbonoMs;
        private final boolean fallarAbono;

        public TransferenciaService(TransferenciaRepository transferencias,
                                    CuentaCliente cuentas,
                                    MovimientoCliente movimientos,
                                    NotificacionCliente notificaciones,
                                    @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
                                    @Value("${mexibanco.demo.fallar-abono:false}") boolean fallarAbono) {
                this.transferencias = transferencias;
                this.cuentas = cuentas;
                this.movimientos = movimientos;
                this.notificaciones = notificaciones;
                this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
                this.fallarAbono = fallarAbono;
        }

        public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
                if (claveOrigen.equals(claveDestino)) {
                        throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
                }
                // Validacion previa: si una cuenta no existe se falla aqui, sin haber movido dinero.
                cuentas.consultar(claveOrigen);
                cuentas.consultar(claveDestino);

                // Paso 1: dejar constancia (INICIADA) antes de tocar dinero.
                Transferencia transferencia = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));

                // Paso 2: cargo al origen.
                var cargo = cargarOMarcarFallida(transferencia, claveOrigen, monto);
                transferencia.cambiarEstado(EstadoTransferencia.CARGADA, null);
                transferencias.save(transferencia);

                pausaDeDemo();

                // Paso 3: abono al destino. Si falla, se compensa el cargo.
                var abono = abonarODeshacer(transferencia, claveOrigen, claveDestino, monto);

                transferencia.cambiarEstado(EstadoTransferencia.COMPLETADA, null);
                transferencias.save(transferencia);

                // Paso 4: asientos y avisos. El dinero ya se movio; si esto falla solo se registra el
                // problema, porque devolver un error por una transferencia ya completada seria enganoso.
                try {
                        var asientoCargo = movimientos.registrar(claveOrigen, TipoMovimiento.TRANSFERENCIA_ENVIADA,
                                monto.negate(), cargo.saldo(), "a " + abono.clabe());
                        var asientoAbono = movimientos.registrar(claveDestino, TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                                monto, abono.saldo(), "de " + cargo.clabe());
                        notificaciones.notificar(asientoCargo);
                        notificaciones.notificar(asientoAbono);
                } catch (RuntimeException e) {
                        log.warn("Transferencia {} completada, pero no se pudo registrar el movimiento o la notificacion: {}",
                                transferencia.getId(), e.getMessage());
                }
                return transferencia;
        }

        /** Intenta el abono; si falla, compensa y lanza TransferenciaFallidaException. */
        private mx.mexibanco.clientes.CuentaRemota abonarODeshacer(Transferencia transferencia, String claveOrigen,
                                                                    String claveDestino, BigDecimal monto) {
                try {
                        if (fallarAbono) {
                                throw new IllegalStateException("Fallo inyectado en el abono (mexibanco.demo.fallar-abono=true)");
                        }
                        return cuentas.abonar(claveDestino, monto);
                } catch (RuntimeException falloAbono) {
                        throw compensar(transferencia, claveOrigen, monto, falloAbono);
                }
        }

        /**
         * Compensacion: un deposito de vuelta a la cuenta origen. Solo se llama desde el estado
         * CARGADA, y el estado COMPENSADA se guarda en cuanto el deposito se confirma, antes de
         * cualquier otra cosa, para que una compensacion ya hecha no se vuelva a aplicar.
         */
        private TransferenciaFallidaException compensar(Transferencia transferencia, String claveOrigen,
                                                        BigDecimal monto, RuntimeException falloAbono) {
                log.warn("Transferencia {}: el abono fallo ({}). Compensando el cargo de {} en {}",
                        transferencia.getId(), falloAbono.getMessage(), monto, claveOrigen);
                String motivo = "Abono fallido: " + falloAbono.getMessage();
                mx.mexibanco.clientes.CuentaRemota devuelta;
                try {
                        devuelta = cuentas.abonar(claveOrigen, monto);
                } catch (RuntimeException falloCompensacion) {
                        log.error("Transferencia {}: la compensacion tambien fallo ({}). Queda COMPENSACION_PENDIENTE",
                                transferencia.getId(), falloCompensacion.getMessage());
                        transferencia.cambiarEstado(EstadoTransferencia.COMPENSACION_PENDIENTE,
                                motivo + " | Compensacion fallida: " + falloCompensacion.getMessage());
                        transferencias.save(transferencia);
                        return new TransferenciaFallidaException(transferencia.getId(),
                                EstadoTransferencia.COMPENSACION_PENDIENTE.name(),
                                "No se pudo abonar al destino ni devolver el cargo al origen: "
                                        + "el dinero esta retenido y requiere intervencion (transferencia "
                                        + transferencia.getId() + ")",
                                falloCompensacion);
                }
                transferencia.cambiarEstado(EstadoTransferencia.COMPENSADA, motivo);
                transferencias.save(transferencia);
                try {
                        movimientos.registrar(claveOrigen, TipoMovimiento.DEPOSITO, monto, devuelta.saldo(),
                                "compensacion transferencia " + transferencia.getId());
                } catch (RuntimeException e) {
                        log.warn("Transferencia {} compensada, pero no se pudo registrar el movimiento: {}",
                                transferencia.getId(), e.getMessage());
                }
                return new TransferenciaFallidaException(transferencia.getId(),
                        EstadoTransferencia.COMPENSADA.name(),
                        "No se pudo abonar al destino; el cargo fue devuelto a la cuenta origen (transferencia "
                                + transferencia.getId() + ")",
                        falloAbono);
        }

        @Transactional(readOnly = true)
        public Transferencia consultar(Long id) {
                return transferencias.findById(id)
                        .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
        }

        private void pausaDeDemo() {
                if (pausaEntreCargoYAbonoMs > 0) {
                        try {
                                Thread.sleep(pausaEntreCargoYAbonoMs);
                        } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                        }
                }
        }

        /** Si el cargo es rechazado no se movio dinero: la saga termina en FALLIDA y el error original sigue su camino. */
        private mx.mexibanco.clientes.CuentaRemota cargarOMarcarFallida(Transferencia transferencia,
                                                                         String claveOrigen, BigDecimal monto) {
                try {
                        return cuentas.cargar(claveOrigen, monto);
                } catch (RuntimeException e) {
                        transferencia.cambiarEstado(EstadoTransferencia.FALLIDA, "Cargo rechazado: " + e.getMessage());
                        transferencias.save(transferencia);
                        throw e;
                }
        }

        /**
         * Reintenta la compensacion de una transferencia que quedo en COMPENSACION_PENDIENTE.
         * Es idempotente: si ya esta COMPENSADA no vuelve a depositar; el estado se guarda en cuanto
         * el deposito se confirma, y es lo que impide devolver el dinero dos veces.
         */
        public Transferencia reintentarCompensacion(Long id) {
                Transferencia transferencia = consultar(id);
                if (transferencia.getEstado() == EstadoTransferencia.COMPENSADA) {
                        return transferencia; // ya compensada: no se hace nada
                }
                if (transferencia.getEstado() != EstadoTransferencia.COMPENSACION_PENDIENTE) {
                        throw new mx.mexibanco.compartido.ConflictoException("La transferencia " + id + " esta "
                                + transferencia.getEstado() + "; solo se compensan las que estan en COMPENSACION_PENDIENTE");
                }
                var devuelta = cuentas.abonar(transferencia.getClaveOrigen(), transferencia.getMonto());
                transferencia.cambiarEstado(EstadoTransferencia.COMPENSADA,
                        "Compensacion reintentada con exito (antes: " + transferencia.getMotivo() + ")");
                transferencias.save(transferencia);
                try {
                        movimientos.registrar(transferencia.getClaveOrigen(), TipoMovimiento.DEPOSITO,
                                transferencia.getMonto(), devuelta.saldo(), "compensacion transferencia " + id);
                } catch (RuntimeException e) {
                        log.warn("Transferencia {} compensada, pero no se pudo registrar el movimiento: {}", id, e.getMessage());
                }
                return transferencia;
        }
}
