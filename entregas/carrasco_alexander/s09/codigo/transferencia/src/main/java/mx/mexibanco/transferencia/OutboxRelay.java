package mx.mexibanco.transferencia;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {

    private final OutboxService servicio;

    public OutboxRelay(OutboxService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${mexibanco.outbox.relay-ms:2000}")
    public void publicar() {
        servicio.publicarPendientes();
    }
}