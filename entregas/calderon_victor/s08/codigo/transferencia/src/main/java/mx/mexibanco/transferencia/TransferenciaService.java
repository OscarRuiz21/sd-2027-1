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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco. En el monolito (v05.1) este metodo era
 * UNA transaccion local: cargo, abono, asientos y avisos se confirmaban juntos o ninguno.
 *
 * En v06a cada paso es una peticion HTTP a otro servicio, con su propia base, y cada una se
 * confirma sola en cuanto responde. transferir() ya NO lleva @Transactional: no hay transaccion
 * que pueda abarcar tres procesos y cuatro bases.
 *
 * Lab S08: este servicio es el ORQUESTADOR de una saga. En lugar de un rollback hay un plan para
 * regresar, y el estado se guarda en la tabla en cada paso (EstadoTransferencia):
 *
 *   paso                              si falla...
 *   1. consultar las dos cuentas      RECHAZADA: no se movio nada
 *   2. cargo a origen                 RECHAZADA: el cargo es local en cuenta; si fallo, no se aplico
 *   3. asiento del cargo y abono      se COMPENSA: se le devuelve el monto a origen (compensar)
 *   4. asiento del abono y avisos     ya no se compensa: el dinero ya llego; se anota lo que falta
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	/** Mientras se intenta una devolucion, la fila queda apartada este tiempo (ver compensar). */
	private static final Duration APARTADA_POR = Duration.ofSeconds(60);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final Set<String> fallaAbonoClabes;
	private final Duration primeraEspera;
	private final Duration esperaMaxima;
	private final Clock reloj;

	@Autowired
	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.falla-abono-clabe:}") String fallaAbonoClabe,
								@Value("${mexibanco.saga.primera-espera-s:2}") long primeraEsperaS,
								@Value("${mexibanco.saga.espera-maxima-s:30}") long esperaMaximaS) {
		this(transferencias, cuentas, movimientos, notificaciones, pausaEntreCargoYAbonoMs, fallaAbonoClabe,
			Duration.ofSeconds(primeraEsperaS), Duration.ofSeconds(esperaMaximaS), Clock.systemDefaultZone());
	}

	TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas, MovimientoCliente movimientos,
						 NotificacionCliente notificaciones, long pausaEntreCargoYAbonoMs, String fallaAbonoClabe,
						 Duration primeraEspera, Duration esperaMaxima, Clock reloj) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.fallaAbonoClabes = Arrays.stream(fallaAbonoClabe.split(",")).map(String::trim)
			.filter(clabe -> !clabe.isEmpty()).collect(Collectors.toUnmodifiableSet());
		this.primeraEspera = primeraEspera;
		this.esperaMaxima = esperaMaxima;
		this.reloj = reloj;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		// La fila existe antes de mover un peso: desde aqui la base sabe en que paso va.
		Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto, reloj.instant()));
		log.info("transferencia {}: INICIADA ({} -> {}, {})", t.getId(), claveOrigen, claveDestino, monto);

		// Pasos 1 y 2. Si algo falla aqui, cuenta no aplico nada: no hay nada que deshacer.
		CuentaRemota origen;
		try {
			cuentas.consultar(claveOrigen);
			cuentas.consultar(claveDestino);
			origen = cuentas.cargar(claveOrigen, monto); // confirmado en cuenta desde aqui
		} catch (RuntimeException e) {
			t.rechazada("No se movio dinero: " + e.getMessage(), reloj.instant());
			t = transferencias.save(t);
			log.info("transferencia {}: RECHAZADA ({})", t.getId(), e.getMessage());
			throw new TransferenciaFallidaException(t, codigoHttp(e), t.getDetalle());
		}
		t.cargada(origen.saldo(), reloj.instant());
		t = transferencias.save(t);
		log.info("transferencia {}: CARGADA (origen queda en {})", t.getId(), origen.saldo());

		// Paso 3. El dinero ya salio de origen: si algo falla aqui, se compensa.
		MovimientoRemoto cargo;
		CuentaRemota destino;
		try {
			cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA, monto.negate(),
				origen.saldo(), "a " + claveDestino + " (transferencia " + t.getId() + ")");
			pausaDeDemo();
			fallaProvocada(claveDestino);
			destino = cuentas.abonar(claveDestino, monto);
		} catch (RuntimeException e) {
			log.warn("transferencia {}: fallo despues del cargo ({}); se compensa", t.getId(), e.getMessage());
			t.porCompensar("El abono a " + claveDestino + " fallo: " + e.getMessage());
			t = compensar(t);
			throw new TransferenciaFallidaException(t, 503, t.getMotivo() + ". " + t.getDetalle());
		}
		t.abonada(destino.saldo(), reloj.instant());
		t = transferencias.save(t);
		log.info("transferencia {}: ABONADA (destino queda en {})", t.getId(), destino.saldo());

		// Paso 4. Despues del abono ya no se compensa (el dinero llego): lo que falte se anota.
		MovimientoRemoto abono;
		try {
			abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA, monto,
				destino.saldo(), "de " + claveOrigen + " (transferencia " + t.getId() + ")");
		} catch (RuntimeException e) {
			t.anotar("No se pudo registrar el asiento del abono: " + e.getMessage(), reloj.instant());
			log.warn("transferencia {}: se queda ABONADA, falta el asiento del abono ({})", t.getId(), e.getMessage());
			return transferencias.save(t);
		}
		t.completada(reloj.instant());
		t = transferencias.save(t);
		log.info("transferencia {}: COMPLETADA", t.getId());

		// Los avisos van al final porque no se pueden deshacer (un aviso enviado no se "desenvia").
		avisar(t, cargo);
		avisar(t, abono);
		return t;
	}

	/**
	 * La compensacion del paso 2: devolverle el monto a origen y dejarlo escrito como movimiento. La
	 * llama la saga (primer intento) y ReintentoDeCompensaciones (los siguientes, con espera creciente).
	 *
	 * Para que repetirla no devuelva dos veces: solo actua si la transferencia esta CARGADA o
	 * COMPENSACION_PENDIENTE; antes de llamar a cuenta aparta la fila (si otro proceso ya la aparto,
	 * el @Version hace que este guardado truene y no se llama a cuenta); y en cuanto cuenta confirma
	 * la devolucion, lo primero que se hace es guardar COMPENSADA.
	 */
	Transferencia compensar(Transferencia t) {
		if (t.getEstado() != EstadoTransferencia.CARGADA && t.getEstado() != EstadoTransferencia.COMPENSACION_PENDIENTE) {
			return t; // ya se devolvio (o nunca hubo cargo): repetirla no hace nada
		}
		int intento = t.nuevoIntentoDeCompensacion(reloj.instant().plus(APARTADA_POR));
		t = transferencias.save(t);

		CuentaRemota origen;
		try {
			origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto());
		} catch (RuntimeException e) {
			Duration espera = esperaAntesDelIntento(intento + 1);
			t.compensacionPendiente("La devolucion fallo en el intento " + intento + " (" + e.getMessage()
				+ "); el siguiente intento va en " + espera.toSeconds() + " s", reloj.instant(), reloj.instant().plus(espera));
			t = transferencias.save(t);
			log.warn("transferencia {}: COMPENSACION_PENDIENTE, el intento {} de devolver fallo ({}); siguiente en {} s",
				t.getId(), intento, e.getMessage(), espera.toSeconds());
			return t;
		}
		t.compensada("Se devolvieron " + t.getMonto() + " a " + t.getClaveOrigen() + " (queda en " + origen.saldo()
			+ ") en el intento " + intento, reloj.instant());
		t = transferencias.save(t);
		log.info("transferencia {}: COMPENSADA en el intento {} (origen regresa a {})", t.getId(), intento, origen.saldo());

		try {
			movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA, t.getMonto(), origen.saldo(),
				"devolucion de la transferencia " + t.getId() + " (no se pudo abonar a " + t.getClaveDestino() + ")");
		} catch (RuntimeException e) {
			t.anotar("No se pudo registrar el asiento de la devolucion: " + e.getMessage(), reloj.instant());
			t = transferencias.save(t);
			log.warn("transferencia {}: falta el asiento de la devolucion ({})", t.getId(), e.getMessage());
		}
		return t;
	}

	/** Espera creciente entre intentos de devolver: 2 s antes del segundo, luego 4, 8, 16... hasta 30 s. */
	Duration esperaAntesDelIntento(int intento) {
		Duration espera = primeraEspera.multipliedBy(1L << Math.min(Math.max(intento - 2, 0), 20));
		return espera.compareTo(esperaMaxima) > 0 ? esperaMaxima : espera;
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	private void avisar(Transferencia t, MovimientoRemoto asiento) {
		try {
			notificaciones.notificar(asiento);
		} catch (RuntimeException e) {
			log.warn("transferencia {}: no se pudo mandar un aviso ({}); el dinero ya quedo bien", t.getId(), e.getMessage());
		}
	}

	/**
	 * Lab S08: para probar la compensacion con cuenta arriba. Si la CLABE destino esta en
	 * FALLA_ABONO_CLABE, el abono falla aqui mismo, sin llamar a cuenta (y sin contar para el circuito).
	 */
	private void fallaProvocada(String claveDestino) {
		if (fallaAbonoClabes.contains(claveDestino)) {
			throw new IllegalStateException("falla provocada con FALLA_ABONO_CLABE=" + claveDestino + ", no se llamo a cuenta");
		}
	}

	private static int codigoHttp(RuntimeException e) {
		return e instanceof ErrorRemotoException remoto ? remoto.getEstado() : 500;
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
