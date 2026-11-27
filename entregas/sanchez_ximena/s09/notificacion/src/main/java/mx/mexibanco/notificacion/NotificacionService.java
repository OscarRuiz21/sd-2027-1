package mx.mexibanco.notificacion;

import mx.mexibanco.notificacion.mensajeria.EventoProcesado;
import mx.mexibanco.notificacion.mensajeria.EventoProcesadoRepository;
import mx.mexibanco.notificacion.mensajeria.EventoTransferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final EventoProcesadoRepository eventosProcesados;

	public NotificacionService(NotificacionRepository notificaciones,
							   EventoProcesadoRepository eventosProcesados) {
		this.notificaciones = notificaciones;
		this.eventosProcesados = eventosProcesados;
	}

	@Transactional
	public Notificacion notificar(AvisoDeMovimiento movimiento) {
		String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs() + ", saldo resultante " + movimiento.saldoResultante();
		log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
		return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
	}

	@Transactional
	public void procesarEventoTransferencia(EventoTransferencia evento) {
		if (evento == null || evento.eventoId() == null || evento.eventoId().isBlank() || evento.tipo() == null) {
			throw new IllegalArgumentException("Evento de transferencia incompleto o malformado");
		}
		if (eventosProcesados.existsById(evento.eventoId())) {
			log.info("[notificacion] evento duplicado ignorado: {}", evento.eventoId());
			return;
		}
		eventosProcesados.save(new EventoProcesado(evento.eventoId()));

		if ("TransferenciaRealizada".equals(evento.tipo())) {
			String msgOrigen = "TRANSFERENCIA_ENVIADA por " + evento.monto().abs() + ", saldo resultante " + evento.saldoOrigen();
			String msgDestino = "TRANSFERENCIA_RECIBIDA por " + evento.monto().abs() + ", saldo resultante " + evento.saldoDestino();
			log.info("[notificacion] {} · {}", evento.clabeOrigen(), msgOrigen);
			log.info("[notificacion] {} · {}", evento.clabeDestino(), msgDestino);
			notificaciones.save(new Notificacion(evento.clabeOrigen(), msgOrigen));
			notificaciones.save(new Notificacion(evento.clabeDestino(), msgDestino));
		} else if ("TransferenciaRevertida".equals(evento.tipo())) {
			String msgRevertida = "DEVOLUCION_TRANSFERENCIA por " + evento.monto().abs() + ", saldo resultante " + evento.saldoOrigen();
			log.info("[notificacion] {} · {}", evento.clabeOrigen(), msgRevertida);
			notificaciones.save(new Notificacion(evento.clabeOrigen(), msgRevertida));
		} else {
			throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
		}
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}