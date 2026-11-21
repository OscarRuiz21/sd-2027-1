package mx.mexibanco.transferencia;

import mx.mexibanco.mensajeria.EventoTransferencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final TransferenciaRepository transferencias;
    private final EventoOutboxRepository outbox;

    public OutboxService(TransferenciaRepository transferencias, EventoOutboxRepository outbox) {
        this.transferencias = transferencias;
        this.outbox = outbox;
    }

    @Transactional
    public Transferencia guardarYEncolar(Transferencia transferencia, EventoTransferencia evento) {
        Transferencia guardada = transferencias.save(transferencia);
        outbox.save(new EventoOutbox(evento));
        return guardada;
    }
}
