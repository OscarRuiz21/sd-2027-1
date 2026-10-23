package mx.mexibanco.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relay programado que cada segundo busca las filas pendientes en la tabla outbox,
 * las publica con PublicadorDeEventos y las marca como publicadas.
 * Si el broker no confirma, la fila sigue pendiente para la siguiente vuelta.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxService outboxService;

    public OutboxRelay(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${mexibanco.outbox.intervalo-ms:1000}",
               initialDelayString = "${mexibanco.outbox.intervalo-ms:2000}")
    public void vaciarOutbox() {
        try {
            int publicados = 0;
            while (publicados < 50) {
                boolean procesado = outboxService.publicarSiguiente();
                if (!procesado) {
                    break;
                }
                publicados++;
            }
        } catch (EventoNoPublicadoException e) {
            log.warn("Outbox relay: el broker no está disponible ({}); se reintentará en el siguiente segundo", e.getMessage());
        } catch (Exception e) {
            log.error("Outbox relay: error al vaciar outbox: {}", e.getMessage(), e);
        }
    }
}
