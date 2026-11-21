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
 * LAB S09: este servicio tambien guardara los avisos que lleguen por RabbitMQ (ver
 * mensajeria.ConsumidorDeTransferencias).
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
	 * Idempotente (tarea S09): la marca del eventoId va en la MISMA transaccion que los avisos. Si el
	 * evento ya estaba marcado, no guarda nada y regresa 0 avisos; el consumidor hace ack igual. Si algo
	 * falla despues de la marca, el rollback se lleva marca y avisos juntos y la siguiente entrega
	 * empieza desde cero.
	 *
	 * Un evento que nunca se va a poder procesar (le faltan campos o el tipo no existe) lanza
	 * IllegalArgumentException antes de tocar la base: el consumidor lo manda a la DLQ.
	 */
	@Transactional
	public List<Notificacion> avisarDeTransferencia(EventoTransferencia evento) {
		validar(evento);
		if (procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId()) == 0) {
			return List.of();   // ya lo habiamos procesado: 0 avisos nuevos, y el consumidor hace ack
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

	private static void validar(EventoTransferencia evento) {
		if (evento == null || evento.eventoId() == null || evento.transferenciaId() == null || evento.tipo() == null) {
			throw new IllegalArgumentException("El mensaje no trae eventoId, transferenciaId o tipo: " + evento);
		}
		boolean realizada = EventoTransferencia.REALIZADA.equals(evento.tipo());
		if (!realizada && !EventoTransferencia.REVERTIDA.equals(evento.tipo())) {
			throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
		}
		if (evento.clabeOrigen() == null || evento.monto() == null || evento.saldoOrigen() == null
			|| (realizada && (evento.clabeDestino() == null || evento.saldoDestino() == null))) {
			throw new IllegalArgumentException("Al evento " + evento.eventoId() + " le faltan datos para el aviso: " + evento);
		}
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
