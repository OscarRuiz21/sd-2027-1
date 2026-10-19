package mx.mexibanco.mensajeria.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.mensajeria.EventoTransferencia;
import mx.mexibanco.transferencia.Transferencia;
import mx.mexibanco.transferencia.TransferenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reto S09: guarda el estado final de la saga Y su evento en UNA transaccion local de la base de
 * transferencia. O quedan las dos cosas, o ninguna: ya no hay doble escritura base + broker en este
 * punto. La saga deja de hablar con RabbitMQ; de eso se encarga RelayDeEventos.
 *
 * Es un bean aparte (y no un metodo privado de TransferenciaService) a proposito: @Transactional solo
 * funciona en un metodo publico llamado DESDE OTRO bean, porque lo aplica el proxy de Spring.
 */
@Component
public class BandejaDeSalida {

	private static final Logger log = LoggerFactory.getLogger(BandejaDeSalida.class);

	private final TransferenciaRepository transferencias;
	private final OutboxRepository outbox;
	private final ObjectMapper json;

	public BandejaDeSalida(TransferenciaRepository transferencias, OutboxRepository outbox, ObjectMapper json) {
		this.transferencias = transferencias;
		this.outbox = outbox;
		this.json = json;
	}

	@Transactional
	public Transferencia guardarConEvento(Transferencia t, EventoTransferencia evento) {
		Transferencia guardada = transferencias.save(t);
		outbox.save(new EventoSaliente(evento, aJson(evento)));
		log.info("Transferencia {}: {} (evento {} {} en el outbox)", guardada.getId(), guardada.getEstado(),
			evento.tipo(), evento.eventoId());
		return guardada;
	}

	private String aJson(EventoTransferencia evento) {
		try {
			return json.writeValueAsString(evento);
		} catch (JsonProcessingException imposible) {
			throw new IllegalStateException("No se pudo convertir a JSON el evento " + evento.eventoId(), imposible);
		}
	}
}
