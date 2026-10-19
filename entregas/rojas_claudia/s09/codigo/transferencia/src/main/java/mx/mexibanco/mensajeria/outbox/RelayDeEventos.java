package mx.mexibanco.mensajeria.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.mensajeria.EventoTransferencia;
import mx.mexibanco.mensajeria.PublicadorDeEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reto S09: el relay del Transactional Outbox. Cada RELAY_OUTBOX_MS (1 s) toma las filas pendientes
 * (FOR UPDATE SKIP LOCKED), las publica con PublicadorDeEventos, que ESPERA la confirmacion del
 * broker, y las marca como publicadas en la misma transaccion.
 *
 *  - Si el broker no confirma (caido, nack, ninguna cola), la fila sigue pendiente y se corta la
 *    vuelta: se reintenta en la siguiente, en orden.
 *  - Si el relay publica y muere ANTES del commit que marca la fila, el evento sale OTRA VEZ en la
 *    siguiente vuelta, con el MISMO eventoId. Lo salva el consumidor idempotente de notificacion.
 *    Es at-least-once de punta a punta.
 *
 * Simplificacion: la transaccion queda abierta mientras se espera cada confirmacion (hasta 5 s por
 * evento); con lotes chicos y una sola replica no estorba.
 */
@Component
public class RelayDeEventos {

	private static final Logger log = LoggerFactory.getLogger(RelayDeEventos.class);
	static final int LOTE = 50;

	private final OutboxRepository outbox;
	private final PublicadorDeEventos publicador;
	private final ObjectMapper json;
	private final TransactionTemplate transaccion;

	public RelayDeEventos(OutboxRepository outbox, PublicadorDeEventos publicador, ObjectMapper json,
						  TransactionTemplate transaccion) {
		this.outbox = outbox;
		this.publicador = publicador;
		this.json = json;
		this.transaccion = transaccion;
	}

	@Scheduled(fixedDelayString = "${mexibanco.mensajeria.relay-outbox-ms:1000}")
	public void publicarPendientes() {
		Integer publicados = transaccion.execute(estado -> {
			int n = 0;
			for (EventoSaliente pendiente : outbox.tomarPendientes(LOTE)) {
				try {
					publicador.publicar(json.readValue(pendiente.getPayload(), EventoTransferencia.class));
				} catch (Exception sinPublicar) {
					pendiente.registrarFalla(sinPublicar.getMessage());
					log.warn("Outbox: el evento {} {} de la transferencia {} sigue pendiente (intento {}): {}",
						pendiente.getTipo(), pendiente.getEventoId(), pendiente.getTransferenciaId(),
						pendiente.getIntentos(), sinPublicar.getMessage());
					break;   // si el broker no contesta, no tiene caso intentar los demas en esta vuelta
				}
				pendiente.marcarPublicado();
				log.info("Transferencia {}: evento {} publicado desde el outbox ({}, intento {})",
					pendiente.getTransferenciaId(), pendiente.getTipo(), pendiente.getEventoId(), pendiente.getIntentos());
				n++;
			}
			return n;
		});
		if (publicados != null && publicados > 0) {
			log.info("Outbox: {} evento(s) publicado(s) en esta vuelta", publicados);
		}
	}
}
