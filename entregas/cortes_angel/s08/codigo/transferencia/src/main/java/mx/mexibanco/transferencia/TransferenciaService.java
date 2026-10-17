package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.transferencia.Transferencia.Estado;
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

        // 1. Iniciar la Saga (guardar estado inicial)
        Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
        transferencia = transferencias.save(transferencia);

        // 2. Ejecutar Cargo
        CuentaRemota origen = cuentas.cargar(claveOrigen, monto);
        transferencia.setEstado(Estado.CARGADA);
        transferencias.save(transferencia);
        
        pausaDeDemo();
        
        CuentaRemota destino;
        try {
			if (claveDestino.equals("FALSO99")) {
                throw new RuntimeException("Fallo simulado en la cuenta destino");
            }
            // 3. Intentar Abono
            destino = cuentas.abonar(claveDestino, monto);
        } catch (Exception excepcionAbono) {
            // 4. ¡EL ABONO FALLÓ! INICIA LA COMPENSACIÓN (REVERSO)
            compensarCargo(transferencia, excepcionAbono);
            // Detenemos el flujo lanzando un error limpio para el cliente
            throw new ReglaDeNegocioException("La transferencia falló durante el abono hacia " + claveDestino + ". El dinero ha sido devuelto a la cuenta origen.");
        }

        // 5. Todo salió bien, completamos el flujo normal
        transferencia.setEstado(Estado.COMPLETADA);
        transferencias.save(transferencia);

        MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
            monto.negate(), origen.saldo(), "a " + destino.clabe());
        MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
            monto, destino.saldo(), "de " + origen.clabe());

        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);
        
        return transferencia;
    }

    // Método auxiliar que realiza el Reverso
    private void compensarCargo(Transferencia transferencia, Exception excepcionAbono) {
        try {
            CuentaRemota origenReembolsado = cuentas.abonar(transferencia.getClaveOrigen(), transferencia.getMonto());
            transferencia.setEstado(Estado.COMPENSADA);
            transferencias.save(transferencia);
            
            // Registra el reverso para que el cliente lo vea en su estado de cuenta
            movimientos.registrar(origenReembolsado.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                transferencia.getMonto(), origenReembolsado.saldo(), "Reverso de transferencia fallida a " + transferencia.getClaveDestino());
            
        } catch (Exception excepcionCompensacion) {
            // ¡Peligro! Falló el reverso (Ej. se apagó cuenta de nuevo). 
            // Queda pendiente para un proceso nocturno (batch)
            transferencia.setEstado(Estado.COMPENSACION_PENDIENTE);
            transferencias.save(transferencia);
            throw new ReglaDeNegocioException("FALLO CRITICO: La compensación a " + transferencia.getClaveOrigen() + " falló. Estado: COMPENSACION_PENDIENTE.");
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