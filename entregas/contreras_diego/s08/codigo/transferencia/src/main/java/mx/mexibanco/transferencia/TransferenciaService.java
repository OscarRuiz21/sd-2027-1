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

/**
 * Orquestador de la saga de transferencia. Cada paso se confirma en su servicio y el estado se
 * guarda en la tabla despues de cada uno, asi que si este servicio se cae la base dice donde quedo.
 * No lleva @Transactional: cada save() confirma por separado a proposito.
 */
@Service
public class TransferenciaService {

        private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

        private final TransferenciaRepository transferencias;
        private final CuentaCliente cuentas;
        private final MovimientoCliente movimientos;
        private final NotificacionCliente notificaciones;
        private final long pausaEntreCargoYAbonoMs;
        private final String fallaAbonoClabe;

        public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
                                                                MovimientoCliente movimientos, NotificacionCliente notificaciones,
                                                                @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
                                                                @Value("${mexibanco.demo.falla-abono-clabe:}") String fallaAbonoClabe) {
                this.transferencias = transferencias;
                this.cuentas = cuentas;
                this.movimientos = movimientos;
                this.notificaciones = notificaciones;
                this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
                this.fallaAbonoClabe = fallaAbonoClabe;
        }

        public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
                if (claveOrigen.equals(claveDestino)) {
                        throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
                }
                cuentas.consultar(claveOrigen);
                cuentas.consultar(claveDestino);

                // Paso 0: queda registrada desde el inicio (INICIADA)
                Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));

                // Paso 1: cargo al origen
                CuentaRemota origen;
                try {
                        origen = cuentas.cargar(claveOrigen, monto);
                } catch (RuntimeException e) {
                        t.cambiarEstado(EstadoTransferencia.FALLIDA, "No se pudo cargar al origen: " + e.getMessage());
                        transferencias.save(t);
                        throw e; // no hay nada que compensar: el cargo no se aplico
                }
                t.cambiarEstado(EstadoTransferencia.CARGADA, "Cargo aplicado al origen");
                transferencias.save(t);

                pausaDeDemo();

                // Paso 2: abono al destino. Si falla, compensacion.
                CuentaRemota destino;
                try {
                        fallaDeDemo(claveDestino);
                        destino = cuentas.abonar(claveDestino, monto);
                } catch (RuntimeException e) {
                        log.warn("Abono fallido en transferencia {}: {}", t.getId(), e.getMessage());
                        return compensar(t, e);
                }
                t.cambiarEstado(EstadoTransferencia.ABONADA, "Abono aplicado al destino");
                transferencias.save(t);

                // Paso 3: asientos y avisos
                MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                        monto.negate(), origen.saldo(), "a " + destino.clabe());
                MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                        monto, destino.saldo(), "de " + origen.clabe());
                notificaciones.notificar(cargo);
                notificaciones.notificar(abono);

                t.cambiarEstado(EstadoTransferencia.COMPLETADA, "Transferencia completada");
                return transferencias.save(t);
        }

        /**
         * Compensacion: devuelve el monto al origen. Solo corre si la transferencia esta en CARGADA,
         * y marca COMPENSADA en cuanto la devolucion se confirma, antes de escribir el asiento,
         * para que un reintento no devuelva el dinero dos veces.
         */
        private Transferencia compensar(Transferencia t, RuntimeException causa) {
                String motivo = "El abono a " + t.getClaveDestino() + " fallo (" + causa.getMessage() + "). ";

                if (t.getEstado() != EstadoTransferencia.CARGADA
                        && t.getEstado() != EstadoTransferencia.COMPENSACION_PENDIENTE) {
                        return t; // ya compensada o en otro estado: no se repite
                }

                CuentaRemota origen;
                try {
                        origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto());
                } catch (RuntimeException e) {
                        t.cambiarEstado(EstadoTransferencia.COMPENSACION_PENDIENTE,
                                motivo + "No se pudo devolver el monto al origen; queda pendiente de reintento: " + e.getMessage());
                        return transferencias.save(t);
                }

                t.cambiarEstado(EstadoTransferencia.COMPENSADA, motivo + "Se devolvio el monto a la cuenta origen.");
                transferencias.save(t);

                try {
                        movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                                t.getMonto(), origen.saldo(), "devolucion de la transferencia " + t.getId());
                } catch (RuntimeException e) {
                        log.error("Devolucion hecha pero no se pudo registrar el movimiento de la transferencia {}", t.getId(), e);
                }
                return t;
        }

        @Transactional(readOnly = true)
        public Transferencia consultar(Long id) {
                return transferencias.findById(id)
                        .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
        }

        /** Demo: falla el abono hacia una CLABE concreta sin apagar nada. Vacio = desactivado. */
        private void fallaDeDemo(String claveDestino) {
                if (!fallaAbonoClabe.isBlank() && fallaAbonoClabe.equals(claveDestino)) {
                        throw new IllegalStateException("Falla simulada del abono hacia " + claveDestino);
                }
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