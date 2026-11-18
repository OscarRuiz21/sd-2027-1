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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco.
 *
 * S08: este servicio es el ORQUESTADOR de una saga. No hay @Transactional que abarque cuenta,
 * movimiento y notificacion; en su lugar:
 *
 *  1. Cada paso se guarda en la tabla transferencia ANTES de dar el siguiente
 *     (INICIADA -> CARGADA -> ABONADA -> COMPLETADA). Si el proceso muere, la fila dice donde iba.
 *  2. Si el abono falla despues del cargo, se COMPENSA: se le devuelve el monto a origen
 *     (POST /cuentas/{origen}/abonos), se registra el asiento y la transferencia queda COMPENSADA.
 *  3. Si la compensacion tambien falla, queda COMPENSACION_PENDIENTE y RecuperadorDeSagas la
 *     reintenta con espera creciente hasta lograrlo (o hasta agotar intentos: COMPENSACION_FALLIDA).
 *  4. Cargo, abono y devolucion llevan Idempotency-Key derivada del id de la transferencia, asi que
 *     reintentar cualquiera de ellos nunca mueve el dinero dos veces.
 *
 * Antes de devolver, el orquestador PREGUNTA a cuenta si el abono si se aplico (un timeout no
 * significa que no ocurrio) y si el cargo de verdad ocurrio. Asi no devuelve dinero que si llego
 * a destino, ni devuelve un cargo que nunca se hizo.
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String fallaAbonoClabe;
	private final Duration reintentoBase;
	private final Duration reintentoMaximo;
	private final int maxIntentosCompensacion;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.falla-abono-clabe:}") String fallaAbonoClabe,
								@Value("${mexibanco.saga.reintento-base-ms:2000}") long reintentoBaseMs,
								@Value("${mexibanco.saga.reintento-maximo-ms:60000}") long reintentoMaximoMs,
								@Value("${mexibanco.saga.max-intentos-compensacion:12}") int maxIntentosCompensacion) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.fallaAbonoClabe = fallaAbonoClabe == null ? "" : fallaAbonoClabe.trim();
		this.reintentoBase = Duration.ofMillis(reintentoBaseMs);
		this.reintentoMaximo = Duration.ofMillis(reintentoMaximoMs);
		this.maxIntentosCompensacion = maxIntentosCompensacion;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		// Primero que existan las dos (sin mover dinero ni crear la fila): mismos errores que antes.
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		Transferencia t = guardar(new Transferencia(claveOrigen, claveDestino, monto));
		log.info("Saga #{}: INICIADA ({} -> {}, {})", t.getId(), claveOrigen, claveDestino, monto);

		// ---- Paso 1: cargo a origen ----
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto, t.llaveCargo());
		} catch (ErrorRemotoException e) {
			if (e.getEstado() < 500) {
				// Respuesta de negocio (saldo insuficiente, CLABE inexistente): el cargo NO ocurrio.
				t.anotarMotivo(e.getMessage());
				t.pasarA(EstadoTransferencia.RECHAZADA, "Cuenta rechazo el cargo: " + e.getMessage());
				guardar(t);
				log.info("Saga #{}: RECHAZADA ({})", t.getId(), e.getMessage());
				throw e;
			}
			return fallo(t, "El cargo a " + claveOrigen + " fallo: " + e.getMessage(), e);
		} catch (RuntimeException e) {
			return fallo(t, "El cargo a " + claveOrigen + " fallo: " + mensaje(e), e);
		}
		t.pasarA(EstadoTransferencia.CARGADA, "Se cargaron " + monto + " a " + claveOrigen + " (saldo " + origen.saldo() + ")");
		t = guardar(t);
		log.info("Saga #{}: CARGADA", t.getId());

		MovimientoRemoto asientoCargo = asentar(claveOrigen, TipoMovimiento.TRANSFERENCIA_ENVIADA, monto.negate(),
			origen.saldo(), "transferencia #" + t.getId() + " a " + claveDestino);
		if (asientoCargo != null) {
			t.marcarAsientoCargo();
			t = guardar(t);
		}

		pausaDeDemo();

		// ---- Paso 2: abono a destino ----
		CuentaRemota destino;
		try {
			simularFallaDeAbono(claveDestino);
			destino = cuentas.abonar(claveDestino, monto, t.llaveAbono());
		} catch (RuntimeException e) {
			return fallo(t, "El abono a " + claveDestino + " fallo: " + mensaje(e), e);
		}
		t.pasarA(EstadoTransferencia.ABONADA, "Se abonaron " + monto + " a " + claveDestino + " (saldo " + destino.saldo() + ")");
		t = guardar(t);
		log.info("Saga #{}: ABONADA", t.getId());

		// ---- Paso 3: asiento del abono y avisos (despues del punto de no retorno: solo se avanza) ----
		return completar(t, destino.saldo(), asientoCargo);
	}

	/**
	 * COMPENSACION. Idempotente: se puede llamar cuantas veces haga falta (desde el orquestador o
	 * desde el recuperador) y el dinero se devuelve una sola vez, porque la devolucion lleva siempre
	 * la misma Idempotency-Key. Nunca lanza excepcion: el resultado queda en el estado de la fila.
	 */
	public Transferencia compensar(Transferencia t) {
		try {
			// 1) Si el abono SI llego (p. ej. timeout con la respuesta perdida), no se devuelve: se termina.
			if (cuentas.operacionAplicada(t.llaveAbono())) {
				t.pasarA(EstadoTransferencia.ABONADA, "El abono si se habia aplicado en cuenta; se termina en lugar de devolver");
				t = guardar(t);
				log.info("Saga #{}: el abono si llego; se completa", t.getId());
				return completar(t, cuentas.consultar(t.getClaveDestino()).saldo(), null);
			}
			// 2) Si el cargo nunca se aplico, no hay nada que devolver.
			if (!cuentas.operacionAplicada(t.llaveCargo())) {
				t.pasarA(EstadoTransferencia.RECHAZADA, "El cargo nunca se aplico en cuenta; no se movio dinero");
				log.info("Saga #{}: RECHAZADA (el cargo no se aplico)", t.getId());
				return guardar(t);
			}
			// 3) Devolver el cargo a origen.
			CuentaRemota origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto(), t.llaveDevolucion());
			if (!t.isAsientoCargo()) {
				// El cargo ocurrio pero su asiento no se alcanzo a registrar: se registra para que el ledger cuadre.
				if (asentar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_ENVIADA, t.getMonto().negate(),
					origen.saldo().subtract(t.getMonto()), "transferencia #" + t.getId() + " a " + t.getClaveDestino()) != null) {
					t.marcarAsientoCargo();
				}
			}
			MovimientoRemoto devolucion = asentar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_RECIBIDA, t.getMonto(),
				origen.saldo(), "devolucion de la transferencia #" + t.getId() + " (compensacion)");
			t.pasarA(EstadoTransferencia.COMPENSADA, "Se devolvieron " + t.getMonto() + " a " + t.getClaveOrigen()
				+ " (saldo " + origen.saldo() + ") en el intento " + (t.getIntentosCompensacion() + 1));
			t = guardar(t);
			log.warn("Saga #{}: COMPENSADA, se devolvieron {} a {}", t.getId(), t.getMonto(), t.getClaveOrigen());
			notificar(devolucion);
			return t;
		} catch (RuntimeException e) {
			t.compensacionFallo(mensaje(e), reintentoBase, reintentoMaximo, maxIntentosCompensacion);
			log.error("Saga #{}: {} -> {}", t.getId(), t.getEstado(), t.getDetalle());
			return guardar(t);
		}
	}

	/** Para el recuperador: una saga que quedo a medias porque el orquestador se interrumpio. */
	public Transferencia reanudar(Transferencia t) {
		log.warn("Saga #{}: se reanuda, estaba en {} desde {}", t.getId(), t.getEstado(), t.getActualizada());
		if (t.getEstado() == EstadoTransferencia.ABONADA) {
			try {
				return completar(t, cuentas.consultar(t.getClaveDestino()).saldo(), null);
			} catch (RuntimeException e) {
				log.error("Saga #{}: no se pudo completar todavia: {}", t.getId(), mensaje(e));
				return t;
			}
		}
		t.anotarMotivo("El orquestador se interrumpio con la transferencia en " + t.getEstado());
		return compensar(t);
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	@Transactional(readOnly = true)
	public List<Transferencia> listar(EstadoTransferencia estado) {
		return estado == null ? transferencias.findTop50ByOrderByIdDesc() : transferencias.findByEstadoOrderByIdDesc(estado);
	}

	// ------------------------------------------------------------------------------------------

	private Transferencia completar(Transferencia t, BigDecimal saldoDestino, MovimientoRemoto asientoCargo) {
		MovimientoRemoto asientoAbono = asentar(t.getClaveDestino(), TipoMovimiento.TRANSFERENCIA_RECIBIDA, t.getMonto(),
			saldoDestino, "transferencia #" + t.getId() + " de " + t.getClaveOrigen());
		t.pasarA(EstadoTransferencia.COMPLETADA, asientoAbono != null
			? "Transferencia completada"
			: "Transferencia completada; no se pudo registrar el asiento del abono (ver logs)");
		t = guardar(t);
		log.info("Saga #{}: COMPLETADA", t.getId());
		notificar(asientoCargo);
		notificar(asientoAbono);
		return t;
	}

	/** Algo fallo a mitad de la saga: se compensa y se le explica al cliente que paso con su dinero. */
	private Transferencia fallo(Transferencia t, String motivo, RuntimeException causa) {
		t.anotarMotivo(motivo);
		log.warn("Saga #{}: {}. Se compensa", t.getId(), motivo);
		t = compensar(t);
		int codigo = causa instanceof ErrorRemotoException remoto ? remoto.getEstado() : 503;
		String base = "La transferencia #" + t.getId() + " no se completo. " + motivo + ". ";
		return switch (t.getEstado()) {
			case COMPLETADA, ABONADA -> t; // resulto que el abono si se habia aplicado
			case COMPENSADA -> throw new TransferenciaNoCompletadaException(t, codigo,
				base + "Se devolvieron " + t.getMonto() + " a la cuenta " + t.getClaveOrigen() + ".");
			case RECHAZADA -> throw new TransferenciaNoCompletadaException(t, codigo,
				base + "El cargo nunca se aplico: no se movio dinero.");
			default -> throw new TransferenciaNoCompletadaException(t, 503,
				base + "Todavia no se pudo devolver el dinero a " + t.getClaveOrigen()
					+ "; el sistema lo reintentara automaticamente. Consulta GET /api/transferencias/" + t.getId());
		};
	}

	/** Asiento en el ledger. Si movimiento falla no se detiene la saga (el dinero ya se movio); queda en el log. */
	private MovimientoRemoto asentar(String clabe, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldo, String referencia) {
		try {
			return movimientos.registrar(clabe, tipo, monto, saldo, referencia);
		} catch (RuntimeException e) {
			log.error("No se pudo registrar el asiento {} {} de {}: {}", tipo, monto, clabe, mensaje(e));
			return null;
		}
	}

	/** Los avisos no son parte de la saga: si notificacion esta caido, la transferencia sigue siendo valida. */
	private void notificar(MovimientoRemoto movimiento) {
		if (movimiento == null) {
			return;
		}
		try {
			notificaciones.notificar(movimiento);
		} catch (RuntimeException e) {
			log.warn("No se pudo notificar el movimiento {}: {}", movimiento.id(), mensaje(e));
		}
	}

	private Transferencia guardar(Transferencia t) {
		return transferencias.save(t);
	}

	/**
	 * Para probar la compensacion con cuenta arriba: FALLA_ABONO_CLABE=<clabe> hace que el abono a
	 * esa CLABE falle como si cuenta hubiera respondido 503. No llega a llamar a cuenta, asi que
	 * tampoco cuenta como falla para el circuit breaker.
	 */
	private void simularFallaDeAbono(String claveDestino) {
		if (!fallaAbonoClabe.isEmpty() && fallaAbonoClabe.equals(claveDestino)) {
			throw new ErrorRemotoException("cuenta", 503,
				"Falla simulada del abono a " + claveDestino + " (FALLA_ABONO_CLABE)");
		}
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

	private static String mensaje(Throwable e) {
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	}
}
