package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reto S09: la saga deja aqui su evento en lugar de publicarlo. MANDATORY: si alguien lo llama fuera
 * de una transaccion, truena. El chiste es justo que el evento se guarde junto con el estado final
 * (ver TransferenciaService.guardarConEvento).
 */
@Component
public class Outbox {

	private final OutboxRepository filas;
	private final ObjectMapper json;

	public Outbox(OutboxRepository filas, ObjectMapper json) {
		this.filas = filas;
		this.json = json;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void agregar(EventoTransferencia evento) {
		try {
			filas.save(new EventoOutbox(evento, json.writeValueAsString(evento)));
		} catch (JsonProcessingException sinJson) {
			throw new IllegalStateException("No se pudo convertir a JSON el evento " + evento.eventoId(), sinJson);
		}
	}
}
