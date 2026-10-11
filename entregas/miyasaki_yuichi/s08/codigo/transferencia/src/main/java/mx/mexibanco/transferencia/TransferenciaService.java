package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.CompensacionPendienteException;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.compartido.TransferenciaCompensadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco. En el monolito (v05.1) este metodo era
 * UNA transaccion local: cargo, abono, asientos y avisos se confirmaban juntos o ninguno.
 *
 * Desde v06a cada paso es una peticion HTTP a otro servicio, con su propia base, y cada una se
 * confirma sola en cuanto responde: no hay transaccion que pueda abarcar tres procesos y cuatro bases.
 *
 * S08: este servicio es el ORQUESTADOR de una saga. Guarda el estado de la transferencia despues
 * de cada paso y, si el abono falla despues del cargo, ejecuta la compensacion: le devuelve el
 * monto a la cuenta de origen y lo deja escrito en el ledger.
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String fallarAbonoA;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.fallar-abono-a:}") String fallarAbonoA) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.fallarAbonoA = fallarAbonoA;
	}

	/**
	 * La saga. Sin @Transactional a proposito: cada save() se confirma en el momento, asi que si el
	 * servicio se cae a la mitad, la base dice en que paso se quedo.
	 */
	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		// Primero que existan las dos, luego el saldo: el mismo orden de errores que en el monolito.
		// Ojo: entre esta comprobacion y el cargo pasa tiempo de red; ya no es una foto consistente.
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		Transferencia transferencia = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));
		log.info("Transferencia {}: INICIADA", transferencia.getId());

		// Paso 1: cargo. Si falla aqui no salio dinero: no hay nada que compensar.
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto);
		} catch (RuntimeException fallaDelCargo) {
			marcar(transferencia, EstadoTransferencia.FALLIDA, "El cargo fallo: " + fallaDelCargo.getMessage());
			throw fallaDelCargo;
		}
		transferencia = marcar(transferencia, EstadoTransferencia.CARGADA, null);

		pausaDeDemo();

		// Paso 2: abono. Si falla, el cargo ya ocurrio: hay que devolverlo. Se atrapa RuntimeException
		// y no solo ErrorRemotoException: con cuenta apagada llega ResourceAccessException o
		// IllegalStateException ("No instances available") del balanceador.
		CuentaRemota destino;
		try {
			destino = abonarAlDestino(claveDestino, monto);
		} catch (RuntimeException fallaDelAbono) {
			throw compensar(transferencia, origen, fallaDelAbono);
		}
		transferencia = marcar(transferencia, EstadoTransferencia.ABONADA, null);

		// Paso 3: asientos y avisos.
		MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.saldo(), "a " + destino.clabe());
		MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.saldo(), "de " + origen.clabe());
		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);

		return marcar(transferencia, EstadoTransferencia.COMPLETADA, null);
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	/**
	 * La compensacion del cargo: le devuelve el monto al origen y deja en el ledger el cargo y la
	 * devolucion, para que el saldo y los movimientos cuadren. Regresa la excepcion que se le
	 * contesta al cliente.
	 *
	 * La devolucion se asienta como DEPOSITO porque TipoMovimiento esta copiado en movimiento: un
	 * tipo nuevo solo aqui haria que movimiento respondiera 400.
	 */
	private RuntimeException compensar(Transferencia transferencia, CuentaRemota origen, RuntimeException fallaDelAbono) {
		String porQue = "El abono a " + transferencia.getClaveDestino() + " fallo (" + fallaDelAbono.getMessage() + ")";
		log.warn("Transferencia {}: {}. Compensando", transferencia.getId(), porQue);

		CuentaRemota devuelta;
		try {
			devuelta = cuentas.abonar(transferencia.getClaveOrigen(), transferencia.getMonto());
		} catch (RuntimeException fallaDeLaDevolucion) {
			String detalle = porQue + " y la devolucion tambien fallo (" + fallaDeLaDevolucion.getMessage() + ")";
			marcar(transferencia, EstadoTransferencia.COMPENSACION_PENDIENTE, detalle);
			return new CompensacionPendienteException(transferencia.getId(),
				detalle + ". El monto se devolvera a " + transferencia.getClaveOrigen() + " al reintentar");
		}

		String detalle = porQue + "; se devolvieron " + transferencia.getMonto() + " a " + transferencia.getClaveOrigen();
		marcar(transferencia, EstadoTransferencia.COMPENSADA, detalle);
		try {
			movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA, transferencia.getMonto().negate(),
				origen.saldo(), "a " + transferencia.getClaveDestino() + " (revertida)");
			movimientos.registrar(devuelta.clabe(), TipoMovimiento.DEPOSITO, transferencia.getMonto(),
				devuelta.saldo(), "devolucion de transferencia " + transferencia.getId());
		} catch (RuntimeException fallaDelAsiento) {
			// El dinero ya regreso; solo falta el asiento. No se deshace la devolucion por esto.
			log.error("Transferencia {}: compensada, pero no se pudo registrar el movimiento: {}",
				transferencia.getId(), fallaDelAsiento.getMessage());
		}
		return new TransferenciaCompensadaException(transferencia.getId(), detalle);
	}

	private Transferencia marcar(Transferencia transferencia, EstadoTransferencia estado, String detalle) {
		transferencia.cambiarA(estado, detalle);
		log.info("Transferencia {}: {}", transferencia.getId(), estado);
		return transferencias.save(transferencia);
	}

	/**
	 * Solo para probar la compensacion con cuenta arriba: con FALLAR_ABONO_A_CLABE, el abono a esa
	 * CLABE falla a proposito, antes de llamar a cuenta. Vacio (el valor normal) no hace nada.
	 */
	private CuentaRemota abonarAlDestino(String clabe, BigDecimal monto) {
		if (clabe.equals(fallarAbonoA)) {
			throw new ErrorRemotoException("cuenta", 503, "Falla simulada del abono a " + clabe + " (FALLAR_ABONO_A_CLABE)");
		}
		return cuentas.abonar(clabe, monto);
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
