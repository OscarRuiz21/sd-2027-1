package mx.mexibanco.notificacion;

import mx.mexibanco.notificacion.mensajeria.EventoProcesado;
import mx.mexibanco.notificacion.mensajeria.EventoProcesadoRepository;
import mx.mexibanco.notificacion.mensajeria.EventoTransferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuarto concepto de Mexi Banco. Guarda el aviso en su base y lo escribe en el log. En el monolito
 * esto ocurria dentro de la misma transaccion que el movimiento; en v06a es una llamada HTTP aparte
 * y ya no hay transaccion que las una.
 *
 * LAB S09: este servicio tambien guarda los avisos que llegan por RabbitMQ (registrarAvisos, que
 * llama mensajeria.ConsumidorDeTransferencias). POST /notificaciones se queda para spei.
 */
@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

	private final NotificacionRepository notificaciones;
	private final EventoProcesadoRepository procesados;

	public NotificacionService(NotificacionRepository notificaciones, EventoProcesadoRepository procesados) {
		this.notificaciones = notificaciones;
		this.procesados = procesados;
	}

	@Transactional
	public Notificacion notificar(AvisoDeMovimiento movimiento) {
		String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs() + ", saldo resultante " + movimiento.saldoResultante();
		log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
		return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
	}

	/**
	 * LAB S09: los avisos de un evento de transferencia, en UNA transaccion junto con su eventoId.
	 *  - TransferenciaRealizada: dos avisos, a origen (enviaste) y a destino (recibiste).
	 *  - TransferenciaRevertida: uno, a origen (no se realizo y te devolvimos el dinero).
	 * Con idempotente=true, si el eventoId ya estaba no guarda nada y regresa 0 (duplicado). Si dos
	 * copias llegan a la vez, la llave primaria de evento_procesado hace fallar a una
	 * (DataIntegrityViolationException) y su transaccion se deshace con todo y avisos.
	 * Regresa cuantos avisos guardo.
	 */
	@Transactional
	public int registrarAvisos(EventoTransferencia evento, boolean idempotente) {
		if (idempotente) {
			if (procesados.existsById(evento.eventoId())) {
				return 0;
			}
			procesados.saveAndFlush(new EventoProcesado(evento));
		}
		List<Notificacion> avisos = avisosDe(evento);
		for (Notificacion aviso : avisos) {
			log.info("[notificacion] {} · {}", aviso.getClabeCuenta(), aviso.getMensaje());
		}
		notificaciones.saveAll(avisos);
		return avisos.size();
	}

	List<Notificacion> avisosDe(EventoTransferencia e) {
		String id = e.eventoId();
		if (EventoTransferencia.REVERTIDA.equals(e.tipo())) {
			return List.of(new Notificacion(e.clabeOrigen(), "Tu transferencia #" + e.transferenciaId() + " por $"
				+ e.monto() + " a la cuenta " + e.clabeDestino() + " no se realizo y te devolvimos el dinero."
				+ saldo(e.saldoOrigen()), id));
		}
		return List.of(
			new Notificacion(e.clabeOrigen(), "Enviaste $" + e.monto() + " a la cuenta " + e.clabeDestino()
				+ " (transferencia #" + e.transferenciaId() + ")." + saldo(e.saldoOrigen()), id),
			new Notificacion(e.clabeDestino(), "Recibiste $" + e.monto() + " de la cuenta " + e.clabeOrigen()
				+ " (transferencia #" + e.transferenciaId() + ")." + saldo(e.saldoDestino()), id));
	}

	private static String saldo(BigDecimal saldo) {
		return saldo == null ? "" : " Tu saldo es $" + saldo + ".";
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
