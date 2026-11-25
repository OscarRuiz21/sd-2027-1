package mx.mexibanco.transferencia.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EnvioProgramado {

	private static final Logger LOG = LoggerFactory.getLogger(EnvioProgramado.class);
	private final PublicadorOutbox publicador;

	EnvioProgramado(PublicadorOutbox publicador) {
		this.publicador = publicador;
	}

	@Scheduled(fixedDelay = 1500)
	void publicarPendientes() {
		try {
			for (int enviados = 0; enviados < 10 && publicador.enviarSiguiente(); enviados++) {
				// Cada envío confirma su propia fila.
			}
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		} catch (Exception error) {
			LOG.warn("Publicación pendiente: {}", error.getMessage());
		}
	}
}
