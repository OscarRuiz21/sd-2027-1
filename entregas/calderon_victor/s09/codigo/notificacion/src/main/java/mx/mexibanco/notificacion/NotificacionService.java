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
 * Lab S09: este servicio tambien guarda los avisos que llegan por RabbitMQ (ver
 * mensajeria.ConsumidorDeTransferencias y avisarDeTransferencia).
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
	 * Lab S09: los avisos de un evento de transferencia. Por TransferenciaRealizada van dos, a quien
	 * envio y a quien recibio; por TransferenciaRevertida uno, a origen, porque su dinero regreso.
	 * Es una sola transaccion: o se guardan todos los avisos del evento, o ninguno.
	 *
	 * Parte 3, idempotente: el broker entrega AL MENOS una vez, asi que el mismo evento puede llegar
	 * dos veces. Lo primero es marcar su eventoId como procesado, en esta misma transaccion. Si la
	 * marca ya existia, el evento ya se proceso: no se guarda nada y se regresa una lista vacia (el
	 * consumidor igual hace ack). Si algo falla despues de la marca, el rollback se lleva marca y avisos
	 * juntos, y la siguiente entrega lo intenta desde cero.
	 */
	@Transactional
	public List<Notificacion> avisarDeTransferencia(EventoTransferencia evento) {
		if (procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId()) == 0) {
			log.info("[notificacion] el evento {} ya se habia procesado: no se guarda ningun aviso", evento.eventoId());
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
