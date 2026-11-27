package mx.mexibanco.transferencia.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * LAB S09, reto: el RELAY del outbox. Cada RELAY_OUTBOX_MS (2 s) toma las filas sin publicar,
 * las publica en RabbitMQ esperando el publisher confirm y las marca como publicadas.
 *
 *  - Todo en una transaccion con SELECT ... FOR UPDATE SKIP LOCKED (ver OutboxRepository).
 *  - Si una publicacion falla (RabbitMQ caido), se anota el error y se corta la vuelta: el resto
 *    sigue pendiente y se intenta en la siguiente. Cuando RabbitMQ vuelve, sale todo, en orden.
 *  - Puede publicar un evento dos veces (publico, el broker confirmo, y el proceso murio antes de
 *    marcar la fila). Es at-least-once: por eso el consumidor deduplica por eventoId.
 */
@Component
public class RelayDeOutbox {

	private static final Logger log = LoggerFactory.getLogger(RelayDeOutbox.class);

	private final OutboxRepository outbox;
	private final PublicadorDeEventos publicador;
	private final ObjectMapper json;
	private final TransactionTemplate transaccion;
	private final int tamanoLote;

	public RelayDeOutbox(OutboxRepository outbox, PublicadorDeEventos publicador, ObjectMapper json,
						 PlatformTransactionManager transacciones,
						 @Value("${mexibanco.eventos.relay-lote:50}") int tamanoLote) {
		this.outbox = outbox;
		this.publicador = publicador;
		this.json = json;
		this.transaccion = new TransactionTemplate(transacciones);
		this.tamanoLote = tamanoLote;
	}

	@Scheduled(fixedDelayString = "${mexibanco.eventos.relay-ms:2000}", initialDelayString = "${mexibanco.eventos.relay-ms:2000}")
	public void publicarPendientes() {
		Integer publicados = transaccion.execute(estado -> {
			List<EventoPendiente> lote = outbox.tomarPendientes(tamanoLote);
			int enviados = 0;
			for (EventoPendiente pendiente : lote) {
				try {
					publicador.publicar(json.readValue(pendiente.getPayload(), EventoTransferencia.class));
					pendiente.marcarPublicado();
					enviados++;
				} catch (Exception fallo) {
					pendiente.registrarFallo(fallo.getMessage());
					log.warn("Relay: no se pudo publicar {} de la transferencia {} (intento {}): {}. {} evento(s) siguen en el outbox.",
						pendiente.getTipo(), pendiente.getTransferenciaId(), pendiente.getIntentos(), fallo.getMessage(),
						lote.size() - enviados);
					break;
				}
			}
			return enviados;
		});
		if (publicados != null && publicados > 0) {
			log.info("Relay: {} evento(s) del outbox publicados", publicados);
		}
	}
}
