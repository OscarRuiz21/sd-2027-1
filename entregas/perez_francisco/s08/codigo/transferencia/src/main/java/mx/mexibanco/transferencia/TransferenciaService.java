
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

    // Permite provocar una falla para probar la compensacion.
    @Value("${mexibanco.demo.fallar-abono-clabe:}")
    private String clabeFalla;

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

        // PASO 1: Comprobar que las cuentas sean diferentes.
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException(
                "La cuenta origen y destino son la misma"
            );
        }

        // PASO 2: Comprobar que las cuentas existan.
        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        // PASO 3: Crear y guardar la transferencia.
        // El estado inicial es INICIADA.
        Transferencia transferencia = transferencias.save(
            new Transferencia(claveOrigen, claveDestino, monto)
        );

        // PASO 4: Descontar dinero de la cuenta origen.
        CuentaRemota origen = cuentas.cargar(claveOrigen, monto);

        // Guardamos que el cargo ya se realizo.
        transferencia.setEstado("CARGADA");
        transferencia = transferencias.save(transferencia);

        // Pausa utilizada para las pruebas de falla.
        pausaDeDemo();

        CuentaRemota destino;

        try {

            // PASO 5: Provocar una falla solamente si
            // configuramos una CLABE para las pruebas.
            if (claveDestino.equals(clabeFalla)) {
                throw new RuntimeException(
                    "Falla de abono provocada para la prueba"
                );
            }

            // PASO 6: Abonar dinero a la cuenta destino.
            destino = cuentas.abonar(claveDestino, monto);

        } catch (Exception error) {

            // PASO 7: Si falla el abono, intentar
            // devolver el dinero a la cuenta origen.
            try {

                CuentaRemota origenCompensado =
                    cuentas.abonar(claveOrigen, monto);

                // Registrar la devolucion del dinero.
                movimientos.registrar(
                    origenCompensado.clabe(),
                    TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                    monto,
                    origenCompensado.saldo(),
                    "Compensacion por abono fallido"
                );

                // El dinero pudo regresar a origen.
                transferencia.setEstado("COMPENSADA");
                transferencias.save(transferencia);

                throw new ReglaDeNegocioException(
                    "El abono fallo. Se devolvio el dinero al origen."
                );

            } catch (ReglaDeNegocioException errorCompensado) {
                // La compensacion se realizo correctamente.
                throw errorCompensado;

            } catch (Exception errorCompensacion) {

                // No fue posible confirmar la devolucion.
                transferencia.setEstado("COMPENSACION_PENDIENTE");
                transferencias.save(transferencia);

                throw new ReglaDeNegocioException(
                    "Fallo el abono y no se pudo compensar. "
                    + "La transferencia requiere revision."
                );
            }
        }

        // PASO 8: El abono fue exitoso.
        transferencia.setEstado("ABONADA");
        transferencia = transferencias.save(transferencia);

        // Registrar ambos movimientos.
        MovimientoRemoto cargo = movimientos.registrar(
            origen.clabe(),
            TipoMovimiento.TRANSFERENCIA_ENVIADA,
            monto.negate(),
            origen.saldo(),
            "a " + destino.clabe()
        );

        MovimientoRemoto abono = movimientos.registrar(
            destino.clabe(),
            TipoMovimiento.TRANSFERENCIA_RECIBIDA,
            monto,
            destino.saldo(),
            "de " + origen.clabe()
        );

        // PASO 9: Marcar como completada.
        transferencia.setEstado("COMPLETADA");
        transferencia = transferencias.save(transferencia);

        // Las notificaciones se realizan al final.
        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);

        return transferencia;
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

        try {
            Thread.sleep(pausaEntreCargoYAbonoMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
