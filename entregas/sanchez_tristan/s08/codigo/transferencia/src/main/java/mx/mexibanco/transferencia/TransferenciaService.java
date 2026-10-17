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
import mx.mexibanco.compartido.TransferenciaCompensadaException;
import mx.mexibanco.compartido.TransferenciaFallidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Orquestador de la Saga de Transferencia (Mexi Banco S08).
 *
 * Coordina las operaciones distribuidas entre cuenta, movimiento y notificacion.
 * Si el cargo al origen se confirma pero el abono al destino falla, la saga ejecuta
 * una transaccion de compensacion: devuelve el dinero al origen mediante abono,
 * registra el movimiento y actualiza el estado en la base de datos de transferencia.
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String fallarAbonoClabe;

	public TransferenciaService(
			TransferenciaRepository transferencias,
			CuentaCliente cuentas,
			MovimientoCliente movimientos,
			NotificacionCliente notificaciones,
			@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
			@Value("${mexibanco.demo.fallar-abono-clabe:}") String fallarAbonoClabe) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.fallarAbonoClabe = fallarAbonoClabe;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}

		// 1. Guardar estado inicial en la base de datos de la saga
		Transferencia t = new Transferencia(claveOrigen, claveDestino, monto, EstadoTransferencia.INICIADA);
		t.setDetalle("Saga iniciada. Validando existencia de cuentas.");
		t = transferencias.save(t);

		// 2. Comprobacion de existencia de ambas cuentas
		try {
			cuentas.consultar(claveOrigen);
			cuentas.consultar(claveDestino);
		} catch (Exception e) {
			t.setEstado(EstadoTransferencia.FALLIDA);
			t.setDetalle("Fallo en consulta previa: " + e.getMessage());
			transferencias.save(t);
			throw e;
		}

		// 3. Paso 1 de la Saga: Cargo en cuenta origen
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto);
			t.setEstado(EstadoTransferencia.CARGADA);
			t.setDetalle("Cargo aplicado en cuenta origen. Saldo resultante: " + origen.saldo());
			t = transferencias.save(t);
		} catch (Exception e) {
			t.setEstado(EstadoTransferencia.FALLIDA);
			t.setDetalle("Fallo al aplicar cargo en cuenta origen: " + e.getMessage());
			transferencias.save(t);
			throw e;
		}

		pausaDeDemo();

		// 4. Paso 2 de la Saga: Abono en cuenta destino
		CuentaRemota destino;
		try {
			// Mecanismo para simular falla de abono sin apagar cuenta (Requisito 3)
			if ((fallarAbonoClabe != null && !fallarAbonoClabe.isBlank() && claveDestino.equals(fallarAbonoClabe))
					|| claveDestino.endsWith("99")) {
				throw new ErrorRemotoException("cuenta", 500,
						"Falla simulada en abono hacia CLABE destino " + claveDestino + " para probar compensacion");
			}

			destino = cuentas.abonar(claveDestino, monto);
		} catch (Exception eAbono) {
			// El cargo salio bien pero el abono fallo: se activa la compensacion de la saga
			log.error("Fallo el abono a destino {}. Iniciando compensacion de transferencia ID {}: {}",
					claveDestino, t.getId(), eAbono.getMessage());
			compensar(t, claveOrigen, claveDestino, monto, eAbono.getMessage());
			return t;
		}

		// 5. Paso 3: Asientos de ledger en movimiento y notificaciones
		MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
				monto.negate(), origen.saldo(), "a " + destino.clabe());
		MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
				monto, destino.saldo(), "de " + origen.clabe());

		t.setEstado(EstadoTransferencia.COMPLETADA);
		t.setDetalle("Transferencia completada exitosamente.");
		t = transferencias.save(t);

		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);
		return t;
	}

	/**
	 * Compensacion de la Saga: devuelve el monto a la cuenta de origen mediante un abono.
	 * Incluye reintentos con espera creciente (backoff exponencial) para mayor resiliencia.
	 */
	private void compensar(Transferencia t, String claveOrigen, String claveDestino, BigDecimal monto, String errorOriginal) {
		t.setEstado(EstadoTransferencia.COMPENSANDO);
		t.setDetalle("Abono a destino fallo (" + errorOriginal + "). Ejecutando compensacion en origen.");
		t = transferencias.save(t);

		CuentaRemota origenReembolsado = null;
		Exception ultimoError = null;
		int maxReintentos = 3;
		long esperaMs = 500;

		for (int intento = 1; intento <= maxReintentos; intento++) {
			try {
				log.info("Intento {}/{} de compensacion (devolucion a {}) por monto {}",
						intento, maxReintentos, claveOrigen, monto);
				origenReembolsado = cuentas.abonar(claveOrigen, monto);
				break;
			} catch (Exception e) {
				ultimoError = e;
				log.warn("Fallo intento {}/{} de compensacion: {}", intento, maxReintentos, e.getMessage());
				if (intento < maxReintentos) {
					try {
						Thread.sleep(esperaMs);
						esperaMs *= 2; // Espera creciente (backoff exponencial)
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}

		if (origenReembolsado != null) {
			// Compensacion exitosa: se registra el movimiento de reverso en movimiento
			try {
				MovimientoRemoto movCompensacion = movimientos.registrar(
						claveOrigen,
						TipoMovimiento.DEPOSITO,
						monto,
						origenReembolsado.saldo(),
						"Compensacion: transferencia revertida a " + claveDestino
				);
				notificaciones.notificar(movCompensacion);
			} catch (Exception eMov) {
				log.warn("No se pudo registrar asiento de compensacion en ledger: {}", eMov.getMessage());
			}

			t.setEstado(EstadoTransferencia.COMPENSADA);
			t.setDetalle("Abono en destino fallo. Dinero devuelto a cuenta origen. Saldo actual: " + origenReembolsado.saldo());
			transferencias.save(t);

			throw new TransferenciaCompensadaException(t.getId(),
					"La transferencia fallo en el abono a destino (" + claveDestino + "). " +
					"Saga compensada: el saldo fue devuelto a la cuenta origen (" + claveOrigen + "). Estado: COMPENSADA.");
		} else {
			// La compensacion tambien fallo (ej. cuenta sigue caida)
			t.setEstado(EstadoTransferencia.COMPENSACION_PENDIENTE);
			t.setDetalle("Abono fallo y compensacion al origen tampoco pudo completarse: " +
					(ultimoError != null ? ultimoError.getMessage() : "desconocido"));
			transferencias.save(t);

			throw new TransferenciaFallidaException(t.getId(),
					"La transferencia fallo y la compensacion no pudo completarse inmediatamente. " +
					"Estado: COMPENSACION_PENDIENTE (ID: " + t.getId() + "). Requiere reintento asincrono.");
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
