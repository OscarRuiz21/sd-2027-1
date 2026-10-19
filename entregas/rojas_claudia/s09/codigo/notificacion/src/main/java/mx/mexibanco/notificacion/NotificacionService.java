package mx.mexibanco.notificacion;

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
 * S09: tambien guarda los avisos que llegan por RabbitMQ (ver mensajeria.ConsumidorDeTransferencias).
 * El POST /notificaciones sigue vivo porque spei todavia avisa por HTTP.
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
	 * S09: los avisos de un evento de transferencia, en UNA transaccion (o se guardan todos, o ninguno).
	 * TransferenciaRealizada: dos avisos, a quien envio y a quien recibio.
	 * TransferenciaRevertida: uno, a origen, porque su dinero regreso.
	 *
	 * IDEMPOTENTE (tarea del duplicado): la mensajeria entrega al menos una vez, asi que el mismo evento
	 * puede llegar dos veces. Lo primero es marcar el eventoId como procesado, en ESTA misma
	 * transaccion. Si ya estaba marcado, no se guarda nada (0 avisos nuevos) y el consumidor hace ack
	 * igual. Si algo falla despues de la marca, el rollback se lleva marca y avisos juntos y la
	 * siguiente entrega lo intenta desde cero.
	 */
	@Transactional
	public List<Notificacion> avisarDeTransferencia(EventoTransferencia evento) {
		if (procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId()) == 0) {
			log.info("[notificacion] el evento {} ya se habia procesado: no se repiten los avisos", evento.eventoId());
			return List.of();
		}
		List<Notificacion> avisos = switch (evento.tipo()) {
			case EventoTransferencia.REALIZADA -> List.of(
				aviso(evento.clabeOrigen(), "TRANSFERENCIA_ENVIADA", evento.monto(), evento.saldoOrigen()),
				aviso(evento.clabeDestino(), "TRANSFERENCIA_RECIBIDA", evento.monto(), evento.saldoDestino()));
			case EventoTransferencia.REVERTIDA -> List.of(
				aviso(evento.clabeOrigen(), "DEVOLUCION_TRANSFERENCIA", evento.monto(), evento.saldoOrigen()));
			default -> throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
		};
		return notificaciones.saveAll(avisos);
	}

	/** El mismo texto de siempre: tipo, monto sin signo y saldo resultante. */
	private static Notificacion aviso(String clabe, String tipo, BigDecimal monto, BigDecimal saldo) {
		String mensaje = tipo + " por " + monto.abs() + ", saldo resultante " + saldo;
		log.info("[notificacion] {} · {}", clabe, mensaje);
		return new Notificacion(clabe, mensaje);
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
