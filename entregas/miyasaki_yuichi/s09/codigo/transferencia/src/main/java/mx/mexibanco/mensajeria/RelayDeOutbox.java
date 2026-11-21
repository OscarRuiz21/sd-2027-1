package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reto S09: el relay del outbox. Cada OUTBOX_RELAY_MS (1 s por omision) toma las filas pendientes con
 * FOR UPDATE SKIP LOCKED, las publica con PublicadorDeEventos (que espera la confirmacion del broker) y
 * las marca como publicadas, todo en una transaccion. Si el broker no confirma, la fila se queda
 * pendiente con un intento mas y la vuelta se corta: con el broker caido no tiene caso probar las demas.
 *
 * Si el relay publica y muere antes de confirmar su transaccion, la fila sigue pendiente y el evento sale
 * otra vez con el MISMO eventoId. Lo que salva de ese duplicado es notificacion: guarda cada eventoId en
 * evento_procesado y la segunda entrega no deja avisos.
 */
@Component
public class RelayDeOutbox {

	private static final Logger log = LoggerFactory.getLogger(RelayDeOutbox.class);

	private final OutboxRepository outbox;
	private final PublicadorDeEventos eventos;
	private final TransactionTemplate transacciones;
	private final ObjectMapper json;
	private final int lote;

	public RelayDeOutbox(OutboxRepository outbox, PublicadorDeEventos eventos, TransactionTemplate transacciones,
						 ObjectMapper json, @Value("${mexibanco.outbox.lote:20}") int lote) {
		this.outbox = outbox;
		this.eventos = eventos;
		this.transacciones = transacciones;
		this.json = json;
		this.lote = lote;
	}

	@Scheduled(fixedDelayString = "${mexibanco.outbox.relay-ms:1000}")
	public void publicarPendientes() {
		transacciones.executeWithoutResult(estado -> {
			for (EventoOutbox fila : outbox.tomarPendientes(lote)) {
				if (!publicar(fila)) {
					return;   // el broker no confirmo: las demas siguen pendientes para la siguiente vuelta
				}
			}
		});
	}

	/** Regresa false si el broker no confirmo y conviene dejar de intentar en esta vuelta. */
	private boolean publicar(EventoOutbox fila) {
		EventoTransferencia evento;
		try {
			evento = json.readValue(fila.getPayload(), EventoTransferencia.class);
		} catch (JsonProcessingException ilegible) {
			// No deberia pasar (el JSON lo escribio Outbox). Se salta para no atorar a las demas.
			fila.contarIntento();
			log.error("[relay] el evento {} del outbox no se puede leer: {}", fila.getEventoId(), ilegible.getMessage());
			return true;
		}
		try {
			eventos.publicar(evento);
		} catch (EventoNoPublicadoException sinConfirmar) {
			fila.contarIntento();
			log.warn("[relay] evento {} {} de la transferencia {} sigue pendiente (intento {}): {}",
				fila.getTipo(), fila.getEventoId(), fila.getTransferenciaId(), fila.getIntentos(), sinConfirmar.getMessage());
			return false;
		}
		fila.marcarPublicado();
		log.info("[relay] evento {} {} de la transferencia {} publicado (intentos fallidos antes: {})",
			fila.getTipo(), fila.getEventoId(), fila.getTransferenciaId(), fila.getIntentos());
		return true;
	}
}
