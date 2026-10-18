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
		// Primero que existan las dos, luego el saldo: el mismo orden de errores que en el monolito.
		// Ojo: entre esta comprobacion y el cargo pasa tiempo de red; ya no es una foto consistente.
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		//1. Inicio de la saga guardando la transferencia
		Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
    transferencia = transferencias.save(transferencia);

		//2. Se realiza el cargo a la cuenta de origen
		CuentaRemota origen = cuentas.cargar(claveOrigen, monto);   // confirmado en cuenta desde aqui
		transferencia.setEstado(Transferencia.Estado.CARGADA);
		transferencias.save(transferencia);
		
		pausaDeDemo();
		CuentaRemota destino;
		try{

			//Para pruebas: si el destino es "0000", se lanza una excepcion para simular que el abono falla
			if(claveDestino.equals("0000")){
				throw new RuntimeException("Simulando fallo en el abono hacia la CLABE 0000");
			}

			//3. Se intenta realizar el abono a la cuenta de destino
			destino = cuentas.abonar(claveDestino, monto); 
			transferencia.setEstado(Transferencia.Estado.ABONADA);
			transferencias.save(transferencia);
		} catch (Exception e) {
			//4. Si el abono falla, se realiza la trasacción compensatoria para devolver el dinero al origen
			try {
				CuentaRemota origenCompensado = cuentas.abonar(claveOrigen, monto);

			  //Se registra el movimiento del cargo original y el de la devolución
			  movimientos.registrar(claveOrigen, TipoMovimiento.TRANSFERENCIA_ENVIADA, monto.negate(),
          origen.saldo(), "a " + claveDestino + " (fallida)");
        movimientos.registrar(claveOrigen, TipoMovimiento.DEPOSITO, monto,
          origenCompensado.saldo(), "Devolución por fallo en abono a " + claveDestino);

			  transferencia.setEstado(Transferencia.Estado.COMPENSADA);
			  transferencias.save(transferencia);
		
			} catch (Exception ex) {
				// 5. Si la compensación también falla
				transferencia.setEstado(Transferencia.Estado.COMPENSACION_PENDIENTE);
				transferencias.save(transferencia);

				throw new ReglaDeNegocioException("Alerta: El abono falló y no nos pudimos comunicar con Cuenta " + claveOrigen +
          " para devolver el dinero. Estado: COMPENSACION PENDIENTE");
			}
			throw new ReglaDeNegocioException("El abono falló ("+ e.getMessage()+
				  ") Se devolvió el dinero a la cuenta origen. Estado: COMPENSADA");
		}
		//6. Si todo sale bien, se registra el movimiento y se notifica a ambos clientes
		MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.saldo(), "a " + destino.clabe());
		MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.saldo(), "de " + origen.clabe());

		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);

		transferencia.setEstado(Transferencia.Estado.COMPLETADA);
		return transferencias.save(transferencia);
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
