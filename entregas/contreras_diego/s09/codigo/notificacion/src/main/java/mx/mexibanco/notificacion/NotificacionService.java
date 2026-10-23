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

	public NotificacionService(NotificacionRepository notificaciones) {
		this.notificaciones = notificaciones;
	}

	// El original: lo sigue usando NotificacionController (y spei, por HTTP).
	@Transactional
	public Notificacion notificar(AvisoDeMovimiento movimiento) {
		String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs() + ", saldo resultante " + movimiento.saldoResultante();
		log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
		return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
	}

	// Lo nuevo de la S09: los avisos que llegan por RabbitMQ.
	@Transactional
	public List<Notificacion> avisarDeTransferencia(EventoTransferencia evento) {
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