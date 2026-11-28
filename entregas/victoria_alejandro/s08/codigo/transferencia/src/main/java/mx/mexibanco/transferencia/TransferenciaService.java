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
 * Transferencia INTERNA, entre dos cuentas del mismo banco, como SAGA orquestada.
 *
 * En v06a cada paso era una peticion HTTP que se confirmaba sola, y si el abono fallaba despues del
 * cargo el dinero quedaba "en el aire". Aqui:
 *   1. La transferencia se guarda en INICIADA ANTES de mover dinero, y su estado se actualiza despues
 *      de cada paso (cada save() se confirma solo: transferir() sigue SIN @Transactional, a proposito).
 *   2. El cargo es el unico paso que se compensa. Si el abono falla, se devuelve el monto a origen
 *      (compensar) y la transferencia queda COMPENSADA.
 *   3. Si la devolucion tambien falla, queda COMPENSACION_PENDIENTE y la termina ReintentoDeCompensaciones.
 *   4. El abono es el punto de no retorno: si despues fallan los asientos o los avisos, NO se devuelve
 *      el dinero (ya llego a destino); la transferencia se queda en ABONADA para terminarla hacia adelante.
 *
 * Respecto al ejemplo generico (Deque<Paso> + compensar en LIFO): aqui la "pila" tiene a lo mas un
 * elemento, el cargo, asi que se escribe explicito. Y la compensacion va en su propio try: en el
 * ejemplo, si compensar() lanza, la saga nunca se marca como fallida.
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

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
		// Validaciones antes de crear la fila: una peticion invalida no deja rastro en la tabla.
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		// Paso 0: dejar constancia ANTES de tocar dinero.
		Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));

		// Paso 1: cargo. Si falla, no salio dinero: no hay nada que compensar.
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto);
		} catch (RuntimeException e) {
			t.rechazada(mensaje(e));
			transferencias.save(t);
			throw e; // mismo error que antes, p. ej. 422 por saldo insuficiente
		}
		t.cargada(origen.saldo());
		t = transferencias.save(t);

		pausaDeDemo();

		// Paso 2: abono. Si falla, se compensa el cargo.
		CuentaRemota destino;
		try {
			destino = cuentas.abonar(claveDestino, monto, "abono-" + t.getId());
		} catch (RuntimeException e) {
			log.warn("Transferencia {}: fallo el abono a {}; se compensa el cargo", t.getId(), claveDestino, e);
			t.falloElAbono("Fallo el abono a " + claveDestino + ": " + mensaje(e));
			t = compensar(t);
			throw new TransferenciaNoCompletadaException(t);
		}
		t.abonada();
		t = transferencias.save(t);

		// Paso 3: asientos y avisos. Pasado el abono ya no se compensa: si algo falla aqui, la
		// transferencia se queda en ABONADA (el dinero ya esta donde debe) y se termina hacia adelante.
		MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.saldo(), "a " + destino.clabe());
		MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.saldo(), "de " + origen.clabe());
		t.completada();
		t = transferencias.save(t);

		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);
		return t;
	}

	/**
	 * Compensacion del cargo: devuelve el monto a origen y deja los dos asientos en el ledger (el cargo
	 * que si ocurrio y su reverso), para que la suma de movimientos siga cuadrando con el saldo.
	 *
	 * Nunca lanza: si algo falla, la transferencia queda en COMPENSACION_PENDIENTE y se devuelve asi.
	 * La usa transferir() y tambien ReintentoDeCompensaciones, por eso debe poder repetirse sin
	 * devolver el dinero dos veces: el abono de reverso lleva la clave idempotente "reverso-{id}",
	 * y cuenta ignora la segunda vez que recibe la misma clave.
	 */
	public Transferencia compensar(Transferencia t) {
		t.registrarIntentoDeCompensacion();
		try {
			CuentaRemota origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto(), "reverso-" + t.getId());
			movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
				t.getMonto().negate(), t.getSaldoOrigenTrasCargo(),
				"a " + t.getClaveDestino() + " (transferencia " + t.getId() + ", revertida)");
			movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_REVERTIDA,
				t.getMonto(), origen.saldo(), "reverso de la transferencia " + t.getId());
			t.compensada();
			log.info("Transferencia {}: compensada en el intento {}", t.getId(), t.getIntentosCompensacion());
		} catch (RuntimeException e) {
			t.compensacionPendiente(mensaje(e));
			log.error("Transferencia {}: la compensacion fallo (intento {}); queda COMPENSACION_PENDIENTE",
				t.getId(), t.getIntentosCompensacion(), e);
		}
		return transferencias.save(t);
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	private static String mensaje(RuntimeException e) {
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
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
