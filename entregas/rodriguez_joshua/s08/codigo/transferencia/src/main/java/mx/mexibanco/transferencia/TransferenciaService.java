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

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco. En el monolito (v05.1) este metodo era
 * UNA transaccion local: cargo, abono, asientos y avisos se confirmaban juntos o ninguno.
 *
 * En v06a cada paso es una peticion HTTP a otro servicio, con su propia base, y cada una se
 * confirma sola en cuanto responde. transferir() ya NO lleva @Transactional: no hay transaccion
 * que pueda abarcar tres procesos y cuatro bases. Si el cargo sale bien y el abono falla, el
 * dinero ya salio de origen y no llego a destino (ver README, "Lo que se rompio al partir").
 * Arreglarlo con una saga es tema de la S12; aqui se deja roto a proposito.
 */
@Service
public class TransferenciaService {

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
    //Circuit breaker
    private final CircuitBreakerManual circuitBreaker;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs, CircuitBreakerManual circuitBreaker) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        // Circuit Breaker
        this.circuitBreaker = circuitBreaker;
	}

	@Value("${mexibanco.demo.clabe-fallo:}") // Variable para forzar fallo (Punto 3 de tu lab)
	private String clabeParaFallar;

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals("000000000000000000")) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}

    // PUNTO 2: Envolver consultas con Circuit Breaker
    circuitBreaker.ejecutar(() -> { cuentas.consultar(claveOrigen); return true; }, "Consultar Origen");
    circuitBreaker.ejecutar(() -> { cuentas.consultar(claveDestino); return true; }, "Consultar Destino");

    // 1. Iniciar la saga guardando el registro
    Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
    transferencia.setEstado("INICIADA");
    transferencia = transferencias.save(transferencia);

    // PUNTO 2: Envolver cargo con Circuit Breaker
    CuentaRemota origen = circuitBreaker.ejecutar(() -> cuentas.cargar(claveOrigen, monto), "Cargo Origen");
    transferencia.setEstado("CARGADA");
    transferencias.save(transferencia); // Actualizamos estado

    pausaDeDemo();

    try {
        // Punto 3 del Lab: Simular fallo de abono
        if (claveDestino.equals(clabeParaFallar)) {
            throw new RuntimeException("Fallo forzado por variable de entorno para probar la saga");
        }

        // PUNTO 2: Envolver abono con Circuit Breaker
        CuentaRemota destino = circuitBreaker.ejecutar(() -> cuentas.abonar(claveDestino, monto), "Abono Destino");
        
        // Si llegamos aquí, el abono fue exitoso
        transferencia.setEstado("ABONADA");
        transferencias.save(transferencia);

        // Registro de movimientos (camino feliz)
        MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
            monto.negate(), origen.saldo(), "a " + destino.clabe());
        MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
            monto, destino.saldo(), "de " + origen.clabe());

        transferencia.setEstado("COMPLETADA");
        transferencias.save(transferencia);

        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);
        
        return transferencia;

    	} catch (Exception e) {
        // 4. ¡EL ABONO FALLÓ! INICIA LA COMPENSACIÓN
        System.err.println("Fallo en el abono, iniciando compensación: " + e.getMessage());
        
        boolean compensado = false;
        int intentos = 0;
        long esperaMs = 2000; // 2 segundos iniciales

        try {
            // PUNTO 2: Envolver la compensación con Circuit Breaker
            CuentaRemota origenCompensado = circuitBreaker.ejecutar(() -> cuentas.abonar(claveOrigen, monto), "Compensación Origen");
            
            // Registrar que fue una devolución para que no se vea como un depósito normal
			movimientos.registrar(origenCompensado.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
    			monto, origenCompensado.saldo(), "Devolución por fallo a " + claveDestino);

            transferencia.setEstado("COMPENSADA");
            transferencias.save(transferencia);
            
            
        } catch (Exception exCompensacion) {
            // 5. LA COMPENSACIÓN TAMBIÉN FALLÓ (ej. la cuenta se apagó en este momento)
            transferencia.setEstado("COMPENSACION_PENDIENTE");
            transferencias.save(transferencia);
            System.err.println("Fallo CRÍTICO: No se pudo compensar el cargo: " + exCompensacion.getMessage());
            
            throw new RuntimeException("Fallo grave: Dinero debitado pero no se pudo abonar ni compensar. Queda pendiente de revisión.");
        	}
			
			// Lanzamos una excepción para que el controlador devuelva un 500 o mensaje al usuario
            throw new RuntimeException("La transferencia falló y el dinero fue devuelto a su cuenta. Detalle: " + e.getMessage());
    	}

	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	/**
	 * Solo para la demo en clase: abre una ventana entre el cargo y el abono para apagar cuenta a
	 * mano y ver el dinero "en el aire". Con PAUSA_ENTRE_CARGO_Y_ABONO_MS=0 (el valor normal) no hace nada.
	 */
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
