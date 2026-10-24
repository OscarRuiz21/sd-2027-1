package mx.mexibanco.mensajeria;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lab S09, reto: el relay del outbox. Cada OUTBOX_REVISAR_CADA_MS (1 s por omision) le pide al Outbox
 * que publique lo pendiente. Con fixedDelay, Spring no empieza una vuelta hasta que termina la
 * anterior. Esta en su propia clase para que la llamada a publicarPendientes pase por el proxy de
 * Spring y su @Transactional si aplique (llamado desde la misma clase no haria nada).
 */
@Component
public class RelayDelOutbox {

	private final Outbox outbox;

	public RelayDelOutbox(Outbox outbox) {
		this.outbox = outbox;
	}

	@Scheduled(fixedDelayString = "${mexibanco.outbox.revisar-cada-ms:1000}",
		initialDelayString = "${mexibanco.outbox.revisar-cada-ms:1000}")
	void publicar() {
		outbox.publicarPendientes();
	}
}
