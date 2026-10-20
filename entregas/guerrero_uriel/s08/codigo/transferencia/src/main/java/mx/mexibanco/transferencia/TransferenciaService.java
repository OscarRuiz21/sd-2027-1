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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;

/**
 * Saga orquestada. transferir() NO es @Transactional: cada save() confirma su paso, para que si el
 * proceso muere la base diga en cual se quedo.
 *
 * INICIADA -> CARGADA -> ABONADA -> COMPLETADA
 *             CARGADA -> COMPENSADA                (fallo el abono, se devolvio el dinero)
 *             CARGADA -> COMPENSACION_PENDIENTE    (fallo el abono y tambien la devolucion)
 * INICIADA -> FALLIDA                              (fallo el cargo, no salio dinero)
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String fallarAbonoAClabe;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.fallar-abono-a-clabe:}") String fallarAbonoAClabe) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.fallarAbonoAClabe = fallarAbonoAClabe;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto)); // INICIADA
		String base = "transferencia-" + t.getId();

		// Paso 1: cargo
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto, base + "-cargo");
		} catch (ErrorRemotoException | ResourceAccessException | IllegalStateException e) {
			t.cambiarEstado(EstadoTransferencia.FALLIDA, "No se pudo cargar al origen: " + e.getMessage());
			transferencias.save(t);
			if (e instanceof IllegalStateException) { // p. ej. "No instances available for cuenta"
				throw new ErrorRemotoException("cuenta", 503, e.getMessage());
			}
			throw e; // 404 / 422 / 503 por el ManejadorDeErrores
		}
		t.cambiarEstado(EstadoTransferencia.CARGADA, null);
		t = transferencias.save(t);
		MovimientoRemoto cargo = asiento(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.saldo(), "a " + claveDestino);

		pausaDeDemo();

		// Paso 2: abono
		CuentaRemota destino;
		try {
			if (!fallarAbonoAClabe.isBlank() && fallarAbonoAClabe.equals(claveDestino)) {
				throw new ErrorRemotoException("cuenta", 503, "Falla simulada del abono hacia " + claveDestino);
			}
			destino = cuentas.abonar(claveDestino, monto, base + "-abono");
		} catch (ErrorRemotoException | ResourceAccessException | IllegalStateException e) {
			return compensar(t, "Falló el abono a " + claveDestino + ": " + e.getMessage());
		}
		t.cambiarEstado(EstadoTransferencia.ABONADA, null);
		t = transferencias.save(t);
		MovimientoRemoto abono = asiento(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.saldo(), "de " + origen.clabe());

		t.cambiarEstado(EstadoTransferencia.COMPLETADA, null);
		t = transferencias.save(t);

		avisar(cargo);
		avisar(abono);
		return t;
	}

	/**
	 * Devuelve el monto al origen. Idempotente: la clave es fija por transferencia, asi que
	 * repetirla (job, reintento manual) no devuelve el dinero dos veces.
	 */
	public Transferencia compensar(Transferencia t, String causa) {
		t.registrarIntentoCompensacion();
		try {
			CuentaRemota origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto(),
				"transferencia-" + t.getId() + "-compensacion");
			asiento(origen.clabe(), TipoMovimiento.COMPENSACION, t.getMonto(), origen.saldo(),
				"reversa de transferencia " + t.getId());
			t.cambiarEstado(EstadoTransferencia.COMPENSADA, causa + ". Se devolvió el monto al origen.");
		} catch (ErrorRemotoException | ResourceAccessException | IllegalStateException e) {
			t.cambiarEstado(EstadoTransferencia.COMPENSACION_PENDIENTE,
				causa + ". La devolución también falló (" + e.getMessage() + "); queda pendiente.");
		}
		return transferencias.save(t);
	}

	/** El asiento no debe tumbar la saga: el dinero ya se movio. Mejor esfuerzo (ver bitacora). */
	private MovimientoRemoto asiento(String clabe, TipoMovimiento tipo, BigDecimal monto,
									 BigDecimal saldo, String referencia) {
		try {
			return movimientos.registrar(clabe, tipo, monto, saldo, referencia);
		} catch (ErrorRemotoException | ResourceAccessException | IllegalStateException e) {
			log.warn("No se pudo registrar el asiento {} de {}: {}", tipo, clabe, e.getMessage());
			return null;
		}
	}

	private void avisar(MovimientoRemoto asiento) {
		if (asiento == null) {
			return;
		}
		try {
			notificaciones.notificar(asiento);
		} catch (ErrorRemotoException | ResourceAccessException | IllegalStateException e) {
			log.warn("No se pudo notificar: {}", e.getMessage());
		}
	}

		/** false mientras el circuito de cuenta este abierto: no tiene caso reintentar. */
	public boolean cuentaDisponible() {
		return !cuentas.circuitoAbierto();
	}

	public String estadoCircuito() {
		return cuentas.estadoCircuito().name();
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	/** Solo para la demo: abre una ventana entre cargo y abono. Con 0 no hace nada. */
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