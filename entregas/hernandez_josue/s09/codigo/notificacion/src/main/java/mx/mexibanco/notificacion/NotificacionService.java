package mx.mexibanco.notificacion;

import mx.mexibanco.notificacion.mensajeria.EventoTransferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cuarto concepto de Mexi Banco. Guarda el aviso en su base y lo escribe en el log. En el monolito
 * esto ocurria dentro de la misma transaccion que el movimiento; en v06a es una llamada HTTP aparte
 * y ya no hay transaccion que las una.
 *
 * Las transferencias internas llegan por RabbitMQ. SPEI conserva el endpoint HTTP.
 */
@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

	private final NotificacionRepository notificaciones;
	private final JdbcTemplate jdbc;

	public NotificacionService(NotificacionRepository notificaciones, JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.notificaciones = notificaciones;
	}

	@Transactional
	public Notificacion notificar(AvisoDeMovimiento movimiento) {
		String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs() + ", saldo resultante " + movimiento.saldoResultante();
		log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
		return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
	}

	@Transactional
	public boolean registrarEvento(EventoTransferencia evento) {
		int insertados = jdbc.update("""
			INSERT INTO evento_procesado (evento_id) VALUES (?)
			ON CONFLICT (evento_id) DO NOTHING
			""", evento.eventoId());
		if (insertados == 0) {
			return false;
		}

		String referencia = "Transferencia #" + evento.transferenciaId();
		if (evento.realizada()) {
			notificaciones.save(new Notificacion(evento.clabeOrigen(), referencia + " enviada por "
				+ evento.monto() + ", saldo " + evento.saldoOrigen()));
			notificaciones.save(new Notificacion(evento.clabeDestino(), referencia + " recibida por "
				+ evento.monto() + ", saldo " + evento.saldoDestino()));
		} else {
			notificaciones.save(new Notificacion(evento.clabeOrigen(), referencia + " revertida. Se devolvieron "
				+ evento.monto() + ", saldo " + evento.saldoOrigen()));
		}
		return true;
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
