package mx.mexibanco.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.mexibanco.notificacion.mensajeria.EventoTransferencia;
import mx.mexibanco.notificacion.mensajeria.EventoProcesado;
import mx.mexibanco.notificacion.mensajeria.EventoProcesadoRepository;

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
	private final EventoProcesadoRepository eventos;

	public NotificacionService(NotificacionRepository notificaciones, EventoProcesadoRepository eventos) {
		this.notificaciones = notificaciones; this.eventos = eventos;
	}

	@Transactional
	public Notificacion notificar(AvisoDeMovimiento movimiento) {
		String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs() + ", saldo resultante " + movimiento.saldoResultante();
		log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
		return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
	}

	@Transactional
	public void procesar(EventoTransferencia evento) {
		if (eventos.existsById(evento.eventoId())) return;
		eventos.save(new EventoProcesado(evento.eventoId()));
		if ("TransferenciaRealizada".equals(evento.tipo())) {
			notificaciones.save(new Notificacion(evento.clabeOrigen(), "Transferencia realizada por " + evento.monto() + " a " + evento.clabeDestino()));
			notificaciones.save(new Notificacion(evento.clabeDestino(), "Transferencia recibida por " + evento.monto() + " de " + evento.clabeOrigen()));
		} else if ("TransferenciaRevertida".equals(evento.tipo())) {
			notificaciones.save(new Notificacion(evento.clabeOrigen(), "Tu transferencia no se realizó y te devolvimos el dinero (" + evento.monto() + ")"));
		} else throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
