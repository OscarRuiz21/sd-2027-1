package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lab S09, reto: el Transactional Outbox. Resuelve la doble escritura: la base dice COMPLETADA y el
 * broker nunca recibe el evento, porque base y broker son dos sistemas sin una transaccion que los una.
 *
 * La idea es no escribir en dos sistemas a la vez. La saga ya no habla con RabbitMQ: deja el evento en
 * SU base (tabla outbox), en la misma transaccion local que el estado final, asi que o quedan los dos
 * o ninguno. Despues, publicarPendientes (lo llama RelayDelOutbox cada segundo) lo lleva al broker
 * con el PublicadorDeEventos, que espera la confirmacion. Si el broker no esta, la fila sigue
 * pendiente y sale en la siguiente vuelta.
 *
 * Lo que el outbox NO evita: si el relay publica y se cae antes de marcar la fila, el evento sale otra
 * vez. Sale con el mismo eventoId, y el consumidor idempotente de notificacion lo reconoce.
 */
@Component
public class Outbox {

	private static final Logger log = LoggerFactory.getLogger(Outbox.class);
	private static final int LOTE = 20;

	private final OutboxRepository pendientes;
	private final PublicadorDeEventos publicador;
	private final ObjectMapper json;

	public Outbox(OutboxRepository pendientes, PublicadorDeEventos publicador, ObjectMapper json) {
		this.pendientes = pendientes;
		this.publicador = publicador;
		this.json = json;
	}

	/**
	 * Deja el evento en la tabla outbox. MANDATORY: tiene que llamarse DENTRO de la transaccion que
	 * guarda el estado de la transferencia; si alguien lo llama fuera de una, truena en lugar de
	 * guardarlo por su lado (que seria otra vez una doble escritura).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void guardar(EventoTransferencia evento) {
		try {
			pendientes.save(new EventoPendiente(evento, json.writeValueAsString(evento)));
		} catch (JsonProcessingException noSeSerializo) {
			throw new IllegalStateException("No se pudo convertir a JSON el evento " + evento.eventoId(), noSeSerializo);
		}
	}

	/**
	 * Una vuelta del relay: toma los pendientes (bloqueados con FOR UPDATE SKIP LOCKED mientras dura
	 * esta transaccion), los publica en orden y marca cada uno como publicado. En cuanto uno no se
	 * puede publicar, anota el error y deja los demas para la siguiente vuelta: si el broker no
	 * contesta, no tiene caso esperar su timeout una vez por evento. Regresa cuantos publico.
	 */
	@Transactional
	public int publicarPendientes() {
		List<EventoPendiente> lote = pendientes.tomarPendientes(LOTE);
		int publicados = 0;
		for (EventoPendiente pendiente : lote) {
			try {
				publicador.publicar(json.readValue(pendiente.getCarga(), EventoTransferencia.class));
			} catch (RuntimeException | JsonProcessingException sinPublicar) {
				pendiente.anotarFalla(sinPublicar.getMessage());
				log.warn("Outbox: el evento {} de la transferencia {} sigue pendiente (intento {}): {}",
					pendiente.getTipo(), pendiente.getTransferenciaId(), pendiente.getIntentos(), sinPublicar.getMessage());
				break;
			}
			pendiente.marcarPublicado();
			publicados++;
			log.info("Outbox: evento {} de la transferencia {} publicado ({}, intento {})",
				pendiente.getTipo(), pendiente.getTransferenciaId(), pendiente.getEventoId(), pendiente.getIntentos());
		}
		return publicados;
	}
}
