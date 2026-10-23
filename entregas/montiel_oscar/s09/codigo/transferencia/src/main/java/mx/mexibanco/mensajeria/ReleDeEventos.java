package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Publica filas pendientes; SKIP LOCKED reparte el trabajo entre replicas. */
@Component
public class ReleDeEventos {
	private static final Logger log = LoggerFactory.getLogger(ReleDeEventos.class);
	private final EventoOutboxRepository outbox;
	private final PublicadorDeEventos publicador;
	private final ObjectMapper json;
	private final TransactionTemplate transacciones;

	public ReleDeEventos(EventoOutboxRepository outbox, PublicadorDeEventos publicador,
			ObjectMapper json, TransactionTemplate transacciones) {
		this.outbox = outbox;
		this.publicador = publicador;
		this.json = json;
		this.transacciones = transacciones;
	}

	@Scheduled(fixedDelayString = "${mexibanco.mensajeria.reintento-outbox-ms:1000}",
		initialDelayString = "${mexibanco.mensajeria.reintento-outbox-ms:1000}")
	void publicarPendientes() {
		for (int i = 0; i < 20; i++) {
			try {
				EventoTransferencia publicado = transacciones.execute(estado -> {
					var siguiente = outbox.siguientePendiente();
					if (siguiente.isEmpty()) return null;
					EventoOutbox fila = siguiente.get();
					EventoTransferencia evento;
					try {
						evento = json.readValue(fila.getContenido(), EventoTransferencia.class);
					} catch (JsonProcessingException ilegible) {
						throw new IllegalStateException("Evento outbox ilegible: " + fila.getEventoId(), ilegible);
					}
					publicador.publicar(evento);
					fila.marcarPublicado();
					return evento;
				});
				if (publicado == null) return;
				log.info("Transferencia {}: evento {} publicado ({})", publicado.transferenciaId(),
					publicado.tipo(), publicado.eventoId());
			} catch (RuntimeException error) {
				log.warn("Evento outbox pendiente; se reintentara: {}", error.getMessage());
				return;
			}
		}
	}
}
