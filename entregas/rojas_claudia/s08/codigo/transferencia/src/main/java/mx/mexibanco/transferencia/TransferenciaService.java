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

		// 1. Guardar la transferencia como INICIADA
		Transferencia peticion = new Transferencia(claveOrigen, claveDestino, monto);
		peticion.setEstado("INICIADA");
		Transferencia guardada = transferencias.save(peticion);

		try {
			// 2. Ejecutar el cargo al origen
			CuentaRemota origen = cuentas.cargar(claveOrigen, monto);
			guardada.setEstado("CARGADA");
			transferencias.save(guardada);
			
			// 3. Pausa para simular la falla y tomar capturas de la Parte 0
			pausaDeDemo();
			
			// 4. Simulación de fallo para el punto 3 de tu bitácora de la Parte 1
			if ("true".equals(System.getenv("SIMULAR_FALLO_ABONO"))) {
				throw new RuntimeException("Fallo forzado en el abono por variable de entorno");
			}

			// 5. Ejecutar el abono al destino
			CuentaRemota destino = cuentas.abonar(claveDestino, monto);
			guardada.setEstado("COMPLETADA");
			transferencias.save(guardada);
			
			// 6. Registrar movimientos y notificaciones
			MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
				monto.negate(), origen.saldo(), "a " + destino.clabe());
			MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
				monto, destino.saldo(), "de " + origen.clabe());

			notificaciones.notificar(cargo);
			notificaciones.notificar(abono);

			return guardada;

		} catch (Exception e) {
			// 7. COMPENSACIÓN: El abono falló, devolvemos el dinero a la cuenta de origen
			try {
				cuentas.abonar(claveOrigen, monto);
				guardada.setEstado("COMPENSADA");
				transferencias.save(guardada);
			} catch (Exception ex) {
				// 8. FALLA LA COMPENSACIÓN: Si cuenta se cae justo cuando estamos devolviendo el dinero
				guardada.setEstado("COMPENSACION_PENDIENTE");
				transferencias.save(guardada);
			}
			
			// Propagamos el error para que el cliente reciba una respuesta HTTP de fallo
			throw new RuntimeException("La transferencia falló, estado actual: " + guardada.getEstado(), e);
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
