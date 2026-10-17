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

    // Req 3: Inyectado directo al atributo. Si la variable de entorno no existe, usa la CLABE de Beto por defecto.
    @Value("${CLABE_FALLA_ABONO:222222222222222222}")
    private String clabeFallaAbono;

    // El constructor regresa a sus 5 parámetros originales para no romper los tests
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

        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        // PASO 1 (Req 1): Guardamos la transferencia como INICIADA antes de mover dinero
        Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
        transferencia.setEstado(Transferencia.Estado.INICIADA);
        transferencia = transferencias.save(transferencia);

        CuentaRemota origen;
        try {
            // PASO 2: Realizamos el cargo y actualizamos a CARGADA
            origen = cuentas.cargar(claveOrigen, monto);
            transferencia.setEstado(Transferencia.Estado.CARGADA);
            transferencias.save(transferencia);
        } catch (Exception e) {
            throw new ReglaDeNegocioException("No se pudo realizar el cargo inicial: " + e.getMessage());
        }

        pausaDeDemo();

        CuentaRemota destino;
        try {
            // Req 3: Trampa para simular que el abono falla sin apagar el contenedor
            if (claveDestino.equals(clabeFallaAbono)) {
                throw new RuntimeException("Falla simulada: Destino bloqueado por variable CLABE_FALLA_ABONO");
            }

            // PASO 3: Intentamos el abono
            destino = cuentas.abonar(claveDestino, monto);

            // Si llegamos aquí, fue un éxito. Actualizamos a COMPLETADA.
            transferencia.setEstado(Transferencia.Estado.COMPLETADA);
            transferencias.save(transferencia);

            // Registramos movimientos
            MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                monto.negate(), origen.saldo(), "a " + destino.clabe());
            MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, destino.saldo(), "de " + origen.clabe());

            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);

            return transferencia;

        } catch (Exception e) {
            // Req 2: Falló el abono, iniciamos la COMPENSACIÓN
            compensar(transferencia, monto);
            throw new ReglaDeNegocioException("El abono falló, pero tu dinero fue devuelto a tu cuenta. Motivo original: " + e.getMessage());
        }
    }

    private void compensar(Transferencia transferencia, BigDecimal monto) {
        try {
            // Revertimos el cargo devolviendo (abonando) el dinero al origen
            CuentaRemota devolucion = cuentas.abonar(transferencia.getClaveOrigen(), monto);
            
            // Registramos el movimiento de devolución
            movimientos.registrar(transferencia.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                monto, devolucion.saldo(), "DEVOLUCIÓN: Falló transferencia a " + transferencia.getClaveDestino());

            // Actualizamos el estado de la saga
            transferencia.setEstado(Transferencia.Estado.COMPENSADA);
            transferencias.save(transferencia);

        } catch (Exception compensacionEx) {
            // Req 4: Si falla el abono Y falla la compensación
            transferencia.setEstado(Transferencia.Estado.COMPENSACION_PENDIENTE);
            transferencias.save(transferencia);
            throw new RuntimeException("CRÍTICO: Falló abono y TAMBIÉN falló compensación. Estado inconsistente.", compensacionEx);
        }
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
            .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
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