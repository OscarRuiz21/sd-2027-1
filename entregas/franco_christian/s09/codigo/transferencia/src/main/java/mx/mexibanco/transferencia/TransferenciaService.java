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
 * Transferencia INTERNA, entre dos cuentas del mismo banco. En el monolito (v05.1) este metodo era
 * UNA transaccion local: cargo, abono, asientos y avisos se confirmaban juntos o ninguno. En v06a
 * cada paso paso a ser una peticion HTTP a otro servicio que se confirma sola, y si el abono fallaba
 * despues del cargo, el dinero se quedaba en el aire.
 *
 * v08: este servicio es el ORQUESTADOR de una saga. Sigue sin @Transactional alrededor de las
 * llamadas (no hay transaccion que abarque tres procesos y cuatro bases); en su lugar:
 *
 *  - La transferencia tiene un estado (EstadoTransferencia) y se guarda en la base en CADA paso,
 *    cada save() en su propia transaccion local. Si el proceso se cae a la mitad, la fila dice en
 *    que paso se quedo.
 *  - Cada paso tiene su compensacion. El cargo se compensa con una DEVOLUCION (un abono a origen
 *    del mismo monto), que queda en el ledger como movimiento DEVOLUCION_TRANSFERENCIA. No se borra
 *    nada: deshacer es otra operacion de negocio.
 *  - El abono es el punto sin retorno: despues de el ya no se compensa, se termina.
 *  - Si la devolucion tambien falla, la fila queda en COMPENSACION_PENDIENTE y ReintentoDeCompensaciones
 *    la termina en cuanto cuenta vuelva.
 *
 * v09: al terminar la saga, la transferencia PUBLICA un evento en RabbitMQ (TransferenciaRealizada
 * o TransferenciaRevertida) en lugar de llamar por HTTP a notificacion. El aviso ya no puede tumbar
 * la transferencia.
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

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		Transferencia t = guardar(new Transferencia(claveOrigen, claveDestino, monto));

		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto, t.clave("cargo"));
		} catch (RuntimeException cargoRechazado) {
			t.marcarFallida("El cargo a la CLABE " + claveOrigen + " no se hizo: " + cargoRechazado.getMessage());
			guardar(t);
			throw cargoRechazado;
		}
		t.marcarCargada(origen.saldo());
		t = guardar(t);

		pausaDeDemo();

		CuentaRemota destino;
		try {
			destino = abonarADestino(t);
		} catch (RuntimeException abonoFallido) {
			log.warn("Transferencia {}: el abono fallo ({}); se devuelve el cargo", t.getId(), abonoFallido.getMessage());
			return compensar(t, "El abono a la CLABE " + claveDestino + " fallo: " + abonoFallido.getMessage());
		}
		t.marcarAbonada();
		t = guardar(t);

		movimientos.registrar(t.clave("asiento-cargo"), claveOrigen,
			TipoMovimiento.TRANSFERENCIA_ENVIADA, monto.negate(), t.getSaldoOrigenTrasCargo(), "a " + claveDestino);
		movimientos.registrar(t.clave("asiento-abono"), claveDestino,
			TipoMovimiento.TRANSFERENCIA_RECIBIDA, monto, destino.saldo(), "de " + claveOrigen);

		t.marcarCompletada();
		t = guardar(t);
		avisar(EventoTransferencia.realizada(t.getEventoId(), t.getId(), claveOrigen, claveDestino, monto,
			t.getSaldoOrigenTrasCargo(), destino.saldo(), t.getActualizada()));
		return t;
	}

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
			return guardar(t);
		}
		t = guardar(t);
		avisar(EventoTransferencia.revertida(t.getEventoId(), t.getId(), t.getClaveOrigen(), t.getClaveDestino(),
			t.getMonto(), origen.saldo(), t.getActualizada()));
		return t;
	}

	public int reintentarCompensacionesPendientes() {
		int terminadas = 0;
		for (Transferencia pendiente : transferencias.findByEstado(COMPENSACION_PENDIENTE)) {
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

	private Transferencia guardar(Transferencia t) {
		Transferencia guardada = transferencias.save(t);
		log.info("Transferencia {}: {}", guardada.getId(), guardada.getEstado());
		return guardada;
	}

	private void avisar(EventoTransferencia evento) {
		try {
			eventos.publicar(evento);
			log.info("Transferencia {}: evento {} publicado ({})", evento.transferenciaId(), evento.tipo(), evento.eventoId());
		} catch (RuntimeException sinPublicar) {
			log.error("Transferencia {}: quedo terminada pero el evento {} {} NO se publico: {}",
				evento.transferenciaId(), evento.tipo(), evento.eventoId(), sinPublicar.getMessage());
		}
	}

	private CuentaRemota abonarADestino(Transferencia t) {
		if (!fallarAbonoAClabe.isEmpty() && fallarAbonoAClabe.equals(t.getClaveDestino())) {
			throw new ErrorRemotoException("cuenta", 503, "Falla provocada con FALLAR_ABONO_A_CLABE=" + fallarAbonoAClabe);
		}
		return cuentas.abonar(t.getClaveDestino(), t.getMonto(), t.clave("abono"));
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
