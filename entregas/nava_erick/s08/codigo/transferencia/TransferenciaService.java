package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.resiliencia.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;


@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);
	private static final int MAX_DETALLE = 300;

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;

	private final CircuitBreaker circuitoCuenta =
		new CircuitBreaker("cuenta", 3, Duration.ofSeconds(10), TransferenciaService::esFallaDeCuenta);

	@Value("${FALLA_ABONO_CLABE:}")
	private String fallaAbonoClabe = "";

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
		circuitoCuenta.ejecutarSinResultado(() -> cuentas.consultar(claveOrigen));
		circuitoCuenta.ejecutarSinResultado(() -> cuentas.consultar(claveDestino));

		// Estado inicial, guardar dinero para que quede historial.
		Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
		transferencias.save(transferencia);

		// Paso 1: cargo al origen.
		CuentaRemota origen;
		try {
			origen = circuitoCuenta.ejecutar(() -> cuentas.cargar(claveOrigen, monto));
		} catch (RuntimeException falla) {
			guardar(transferencia, EstadoTransferencia.FALLIDA, "No se pudo cargar el origen: " + falla.getMessage());
			throw falla;
		}
		guardar(transferencia, EstadoTransferencia.CARGADA, null);
		pausaDeDemo();

		// Paso 2: abono al destino. Si falla, se compensa el cargo.
		CuentaRemota destino;
		try {
			fallaProvocadaDelAbono(claveDestino);
			destino = circuitoCuenta.ejecutar(() -> cuentas.abonar(claveDestino, monto));
		} catch (RuntimeException falla) {
			return compensar(transferencia, origen, falla);
		}
		guardar(transferencia, EstadoTransferencia.ABONADA, null);

		// Paso 3: asientos y avisos. Ya no se puede dar marcha atras: el dinero llego.
		MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.saldo(), "a " + destino.clabe());
		MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.saldo(), "de " + origen.clabe());
		guardar(transferencia, EstadoTransferencia.COMPLETADA, null);

		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);
		return transferencia;
	}

	/**
	 * Compensacion
	 */
	private Transferencia compensar(Transferencia transferencia, CuentaRemota origen, RuntimeException causa) {
		BigDecimal monto = transferencia.getMonto();
		String motivo = "El abono a " + transferencia.getClaveDestino() + " fallo (" + causa.getMessage() + ")";

		CuentaRemota devuelta;
		try {
			devuelta = circuitoCuenta.ejecutar(() -> cuentas.abonar(transferencia.getClaveOrigen(), monto));
		} catch (RuntimeException falla) {
			// La compensacion tambien fallo: Quead pendiente a reintento
			log.error("Transferencia {}: fallo el abono y tambien la devolucion; queda pendiente",
				transferencia.getId(), falla);
			guardar(transferencia, EstadoTransferencia.COMPENSACION_PENDIENTE, motivo
				+ ". La devolucion tambien fallo (" + falla.getMessage() + "); queda pendiente de devolver " + monto);
			return transferencia;
		}

		// El dinero ya volvio
		guardar(transferencia, EstadoTransferencia.COMPENSADA, motivo + ". Se devolvieron " + monto
			+ " a " + transferencia.getClaveOrigen());

		// Asientos el cargo que si ocurrio y la devolucion
		try {
			movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
				monto.negate(), origen.saldo(), "a " + transferencia.getClaveDestino() + " (no se completo)");
			movimientos.registrar(devuelta.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
				monto, devuelta.saldo(), "devolucion de la transferencia " + transferencia.getId());
		} catch (RuntimeException falla) {
			log.warn("Transferencia {}: se devolvio el dinero pero no se pudieron registrar los asientos",
				transferencia.getId(), falla);
		}
		return transferencia;
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	/** Estado actual del circuito de cuenta */
	public Map<String, Object> estadoDelCircuito() {
		return circuitoCuenta.instantanea();
	}

	/**
	 *falla del circuito: que cuenta NO responda (caida, timeout, sin instancias).
	 * Un error de negocio (404, 422, 409) significa que cuenta si contesto, asi que no cuenta.
	 */
	private static boolean esFallaDeCuenta(RuntimeException error) {
		return !(error instanceof NoEncontradoException
			|| error instanceof ReglaDeNegocioException
			|| error instanceof ConflictoException);
	}

	/** Cambia el estado y lo confirma en la base de inmediato. */
	private void guardar(Transferencia transferencia, EstadoTransferencia estado, String detalle) {
		transferencia.cambiarEstado(estado, recortar(detalle));
		transferencias.save(transferencia);
	}

	private String recortar(String texto) {
		if (texto == null || texto.length() <= MAX_DETALLE) {
			return texto;
		}
		return texto.substring(0, MAX_DETALLE - 3) + "...";
	}

	private void fallaProvocadaDelAbono(String claveDestino) {
		if (fallaAbonoClabe != null && !fallaAbonoClabe.isBlank() && fallaAbonoClabe.equals(claveDestino)) {
			throw new IllegalStateException("falla provocada del abono (FALLA_ABONO_CLABE)");
		}
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