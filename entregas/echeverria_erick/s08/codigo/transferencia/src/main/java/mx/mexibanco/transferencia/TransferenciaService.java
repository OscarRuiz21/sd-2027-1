package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Transferencia como saga orquestada: este servicio decide qué paso sigue y qué hacer si uno falla.
 * El estado se guarda en la base después de cada paso (cada save es su propia transacción local).
 * 
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String clabeQueFalla;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.fallar-abono-a:}") String clabeQueFalla) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.clabeQueFalla = clabeQueFalla;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		// Paso 0: la transferencia queda escrita ANTES de mover dinero
		Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));

		// Paso 1: cargo. Si falla, no se movió nada.
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto);
		} catch (RuntimeException e) {
			guardar(t, EstadoTransferencia.FALLIDA, "No se pudo cargar: " + e.getMessage());
			throw e;
		}
		guardar(t, EstadoTransferencia.CARGADA, null);
		pausaDeDemo();

		// Paso 2: abono. Si falla, se compensa (compensar siempre termina lanzando una excepción).
		CuentaRemota destino;
		try {
			provocarFallaSiCorresponde(claveDestino);
			destino = cuentas.abonar(claveDestino, monto);
		} catch (RuntimeException e) {
			compensar(t, e);
			throw e;
		}
		guardar(t, EstadoTransferencia.ABONADA, null);

		// Paso 3: asientos. Si fallan aquí NO se compensa: el dinero ya llegó bien,
		// y la base dice ABONADA, o sea, en qué paso se quedó.
		MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.saldo(), "a " + destino.clabe());
		MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.saldo(), "de " + origen.clabe());
		guardar(t, EstadoTransferencia.COMPLETADA, null);

		// Paso 4: los avisos van al final porque no se pueden "desenviar"
		try {
			notificaciones.notificar(cargo);
			notificaciones.notificar(abono);
		} catch (RuntimeException e) {
			log.warn("Transferencia {} completada, pero falló el aviso: {}", t.getId(), e.getMessage());
		}
		return t;
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	private void guardar(Transferencia t, EstadoTransferencia estado, String detalle) {
		t.marcar(estado, detalle);
		transferencias.save(t);
	}

	/** Compensación del abono: devolver el monto al origen. Siempre termina lanzando 409. */
	private void compensar(Transferencia t, RuntimeException causa) {
		// Idempotencia por estado: solo se devuelve si el dinero sigue "en el aire"
		if (t.getEstado() != EstadoTransferencia.CARGADA
				&& t.getEstado() != EstadoTransferencia.COMPENSACION_PENDIENTE) {
			return;
		}
		try {
			CuentaRemota devuelta = cuentas.abonar(t.getClaveOrigen(), t.getMonto());
			guardar(t, EstadoTransferencia.COMPENSADA, "Falló el abono (" + causa.getMessage() + "); se devolvió el dinero");
			registrarDevolucion(t, devuelta);
		} catch (RuntimeException fallaLaDevolucion) {
			guardar(t, EstadoTransferencia.COMPENSACION_PENDIENTE,
				"Falló el abono y también la devolución: " + fallaLaDevolucion.getMessage());
			throw new ConflictoException("Transferencia " + t.getId() + " en estado COMPENSACION_PENDIENTE: "
				+ "el dinero salió del origen y la devolución quedó pendiente.");
		}
		throw new ConflictoException("Transferencia " + t.getId() + " en estado COMPENSADA: "
			+ "el abono falló y el dinero se devolvió a la cuenta de origen.");
	}

	private void registrarDevolucion(Transferencia t, CuentaRemota devuelta) {
		try {
			movimientos.registrar(devuelta.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA, t.getMonto(),
				devuelta.saldo(), "compensación de la transferencia #" + t.getId());
		} catch (RuntimeException e) {
			log.warn("Devolución hecha, pero sin asiento en el ledger (transferencia {})", t.getId());
		}
	}

	/** Solo para probar: si FALLAR_ABONO_A trae una CLABE, el abono hacia ella falla. */
	private void provocarFallaSiCorresponde(String claveDestino) {
		if (!clabeQueFalla.isBlank() && clabeQueFalla.equals(claveDestino)) {
			throw new ErrorRemotoException("cuenta", 503, "Falla provocada para la prueba");
		}
	}

	/** Solo para la demo: abre una ventana entre el cargo y el abono para apagar cuenta a mano. */
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