package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.mensajeria.EventoTransferencia;
import mx.mexibanco.mensajeria.PublicadorDeEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSACION_PENDIENTE;
import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSADA;

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco. Orquestador de una saga (v08) que en
 * el lab S09 deja de llamar a notificacion por HTTP y publica un evento en RabbitMQ.
 *
 *  - La transferencia tiene un estado y se guarda en la base en CADA paso.
 *  - Cada paso tiene su compensacion (el cargo se compensa con una devolucion).
 *  - El abono es el punto sin retorno.
 *  - Si la devolucion tambien falla, queda COMPENSACION_PENDIENTE y ReintentoDeCompensaciones la termina.
 *
 * Las llamadas a cuenta pasan ademas por un circuit breaker (ver CuentaCliente).
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final PublicadorDeEventos eventos;
	private final long pausaEntreCargoYAbonoMs;
	private final String fallarAbonoAClabe;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, PublicadorDeEventos eventos,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.fallar-abono-a-clabe:}") String fallarAbonoAClabe) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.eventos = eventos;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.fallarAbonoAClabe = fallarAbonoAClabe == null ? "" : fallarAbonoAClabe.trim();
	}

	/**
	 * Corre la saga completa. Regresa la transferencia en COMPLETADA, COMPENSADA o
	 * COMPENSACION_PENDIENTE (el controller decide que le contesta al cliente en cada caso).
	 * Lanza excepcion si se rechaza antes de mover dinero (misma cuenta, CLABE inexistente, cargo
	 * rechazado) o si falla algo despues del abono.
	 */
	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		// La fila nace ANTES de tocar dinero: desde aqui la base sabe que esta transferencia existe.
		Transferencia t = guardar(new Transferencia(claveOrigen, claveDestino, monto));

		// Paso 1, el cargo. Si cuenta lo rechaza o no contesta, no hay nada que compensar: queda FALLIDA.
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto, t.clave("cargo"));
		} catch (RuntimeException cargoRechazado) {
			t.marcarFallida("El cargo a la CLABE " + claveOrigen + " no se hizo: " + cargoRechazado.getMessage());
			guardar(t);
			throw cargoRechazado;
		}
		t.marcarCargada(origen.saldo());
		t = guardar(t); // CARGADA: el dinero salio de origen y todavia no llega a destino

		pausaDeDemo();

		// Paso 2, el abono (el punto sin retorno). Si falla, se compensa el paso 1.
		CuentaRemota destino;
		try {
			destino = abonarADestino(t);
		} catch (RuntimeException abonoFallido) {
			log.warn("Transferencia {}: el abono fallo ({}); se devuelve el cargo", t.getId(), abonoFallido.getMessage());
			return compensar(t, "El abono a la CLABE " + claveDestino + " fallo: " + abonoFallido.getMessage());
		}
		t.marcarAbonada();
		t = guardar(t);

		// Paso 3, despues del pivote: asientos y evento. Ya no se llama a notificacion por HTTP.
		movimientos.registrar(t.clave("asiento-cargo"), claveOrigen,
			TipoMovimiento.TRANSFERENCIA_ENVIADA, monto.negate(), t.getSaldoOrigenTrasCargo(), "a " + claveDestino);
		movimientos.registrar(t.clave("asiento-abono"), claveDestino,
			TipoMovimiento.TRANSFERENCIA_RECIBIDA, monto, destino.saldo(), "de " + claveOrigen);

		t.marcarCompletada();
		t = guardar(t); // primero la base dice COMPLETADA
		avisar(EventoTransferencia.realizada(t.getEventoId(), t.getId(), claveOrigen, claveDestino, monto,
			t.getSaldoOrigenTrasCargo(), destino.saldo(), t.getActualizada())); // luego el recado
		return t;
	}

	/**
	 * La compensacion del cargo: devolverle el monto a origen y dejarlo escrito en el ledger.
	 * Si sale bien, COMPENSADA (y se publica TransferenciaRevertida). Si no, COMPENSACION_PENDIENTE.
	 *
	 * Es idempotente: cada llamada lleva una clave fija derivada de la saga (Idempotency-Key), asi
	 * cuenta y movimiento no aplican dos veces el mismo paso si hay reintento.
	 */
	private Transferencia compensar(Transferencia t, String motivo) {
		CuentaRemota origen;
		try {
			origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto(), t.clave("devolucion"));
			movimientos.registrar(t.clave("asiento-cargo"), t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
				t.getMonto().negate(), t.getSaldoOrigenTrasCargo(), "a " + t.getClaveDestino());
			movimientos.registrar(t.clave("asiento-devolucion"), t.getClaveOrigen(), TipoMovimiento.DEVOLUCION_TRANSFERENCIA,
				t.getMonto(), origen.saldo(), "devolucion de la transferencia " + t.getId() + ": no llego a " + t.getClaveDestino());
			t.marcarCompensada(motivo);
		} catch (RuntimeException devolucionFallida) {
			log.error("Transferencia {}: la devolucion tambien fallo ({}); queda COMPENSACION_PENDIENTE",
				t.getId(), devolucionFallida.getMessage());
			t.marcarCompensacionPendiente(motivo);
			return guardar(t); // nada que avisar: el dinero no ha regresado
		}
		t = guardar(t);
		avisar(EventoTransferencia.revertida(t.getEventoId(), t.getId(), t.getClaveOrigen(),
			t.getClaveDestino(), t.getMonto(), origen.saldo(), t.getActualizada()));
		return t;
	}

	/**
	 * Si publicar falla, la transferencia NO se revierte: el dinero ya se movio bien y el cliente
	 * recibe su 201. Solo se pierde el aviso, y queda en el log.
	 */
	private void avisar(EventoTransferencia evento) {
		try {
			eventos.publicar(evento);
			log.info("Transferencia {}: evento {} publicado ({})", evento.transferenciaId(), evento.tipo(),
				evento.eventoId());
		} catch (RuntimeException sinPublicar) {
			log.error("Transferencia {}: quedo terminada pero el evento {} {} NO se publico: {}",
				evento.transferenciaId(), evento.tipo(), evento.eventoId(), sinPublicar.getMessage());
		}
	}

	/**
	 * Lo llama ReintentoDeCompensaciones cada pocos segundos. Vuelve a intentar la compensacion de
	 * cada transferencia en COMPENSACION_PENDIENTE. Regresa cuantas quedaron COMPENSADA.
	 */
	public int reintentarCompensacionesPendientes() {
		int terminadas = 0;
		for (Transferencia pendiente : transferencias.findByEstado(COMPENSACION_PENDIENTE)) {
			// motivo null: se conserva el del abono original
			if (compensar(pendiente, null).getEstado() == COMPENSADA) {
				terminadas++;
			}
		}
		return terminadas;
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	@Transactional(readOnly = true)
	public List<Transferencia> buscarPorEstado(EstadoTransferencia estado) {
		return transferencias.findByEstado(estado);
	}

	/** Cada paso de la saga se guarda en su propia transaccion (la de save) y deja una linea en el log. */
	private Transferencia guardar(Transferencia t) {
		Transferencia guardada = transferencias.save(t);
		log.info("Transferencia {}: {}", guardada.getId(), guardada.getEstado());
		return guardada;
	}

	/**
	 * Solo para probar la compensacion con todo arriba: si FALLAR_ABONO_A_CLABE trae la CLABE destino,
	 * el abono "falla" aqui mismo, como si cuenta hubiera contestado 503. Vacia, no hace nada.
	 */
	private CuentaRemota abonarADestino(Transferencia t) {
		if (!fallarAbonoAClabe.isEmpty() && fallarAbonoAClabe.equals(t.getClaveDestino())) {
			throw new ErrorRemotoException("cuenta", 503, "Falla provocada con FALLAR_ABONO_A_CLABE=" + fallarAbonoAClabe);
		}
		return cuentas.abonar(t.getClaveDestino(), t.getMonto(), t.clave("abono"));
	}

	/**
	 * Solo para la demo en clase: abre una ventana entre el cargo y el abono para apagar cuenta a
	 * mano. Con PAUSA_ENTRE_CARGO_Y_ABONO_MS=0 (el valor normal) no hace nada.
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