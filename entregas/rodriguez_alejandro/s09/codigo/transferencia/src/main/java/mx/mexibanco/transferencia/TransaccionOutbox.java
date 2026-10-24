package mx.mexibanco.transferencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransaccionOutbox {
    
    private final TransferenciaRepository transferencias;
    private final EventoOutboxRepository outbox;
    private final ObjectMapper mapper;

    public TransaccionOutbox(TransferenciaRepository transferencias, EventoOutboxRepository outbox, ObjectMapper mapper) {
        this.transferencias = transferencias;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public Transferencia guardarYPublicar(Transferencia t, String routingKey, TransferenciaEvento evento) {
        try {
            Transferencia guardada = transferencias.save(t);
            outbox.save(new EventoOutbox(routingKey, mapper.writeValueAsString(evento)));
            return guardada;
        } catch (Exception e) {
            throw new RuntimeException("Error en Transactional Outbox", e);
        }
    }
}