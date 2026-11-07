package mx.mexibanco.transferencia;

import mx.mexibanco.mensajeria.PublicadorDeEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final EventoOutboxRepository outbox;
    private final PublicadorDeEventos publicador;

    public OutboxRelay(EventoOutboxRepository outbox, PublicadorDeEventos publicador) {
        this.outbox = outbox;
        this.publicador = publicador;
    }

    @Scheduled(fixedDelayString = "${mexibanco.saga.outbox-ms:1000}",
        initialDelayString = "${mexibanco.saga.outbox-ms:1000}")
    @Transactional
    public void publicarPendientes() {
        for (EventoOutbox pendiente : outbox.tomarPendientesBloqueando()) {
            try {
                publicador.publicar(pendiente.evento());
                pendiente.marcarPublicado();
                outbox.save(pendiente);
                log.info("Outbox: evento {} publicado", pendiente.getEventoId());
            } catch (RuntimeException sinPublicar) {
                log.warn("Outbox: no se pudo publicar el evento {}; queda pendiente: {}",
                    pendiente.getEventoId(), sinPublicar.getMessage());
            }
        }
    }
}
