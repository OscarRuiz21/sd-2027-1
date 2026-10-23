package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final EventoOutboxRepository outboxRepository;
    private final PublicadorDeEventos publicador;
    private final ObjectMapper json;
    private final TransactionTemplate transactionTemplate;

    public OutboxService(EventoOutboxRepository outboxRepository,
                         PublicadorDeEventos publicador,
                         ObjectMapper json,
                         TransactionTemplate transactionTemplate) {
        this.outboxRepository = outboxRepository;
        this.publicador = publicador;
        this.json = json;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Guarda el evento en la tabla outbox (se llama dentro de la misma transacción que el cambio de estado).
     */
    public EventoOutbox guardar(EventoTransferencia evento) {
        String payload;
        try {
            payload = json.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.eventoId() + " a JSON", e);
        }
        return outboxRepository.save(new EventoOutbox(evento.eventoId(), evento.tipo(), evento.transferenciaId(), payload));
    }

    /**
     * Toma el siguiente evento pendiente bloqueándolo con FOR UPDATE SKIP LOCKED, lo publica al broker
     * esperando confirmación y lo marca como publicado. Si el broker no confirma o está caído,
     * la transacción se deshace y el evento sigue pendiente para el siguiente ciclo.
     * Retorna true si procesó un evento, false si no había eventos pendientes.
     */
    public boolean publicarSiguiente() {
        Boolean procesado = transactionTemplate.execute(status -> {
            Optional<EventoOutbox> siguiente = outboxRepository.buscarSiguientePendiente();
            if (siguiente.isEmpty()) {
                return false;
            }

            EventoOutbox outbox = siguiente.get();
            EventoTransferencia evento;
            try {
                evento = json.readValue(outbox.getPayload(), EventoTransferencia.class);
            } catch (JsonProcessingException e) {
                log.error("Outbox {}: JSON corrupto para evento {}: {}",
                    outbox.getId(), outbox.getEventoId(), e.getMessage());
                outbox.marcarPublicado();
                outboxRepository.save(outbox);
                return true;
            }

            // Publica hacia RabbitMQ y espera confirmación del broker
            publicador.publicar(evento);

            outbox.marcarPublicado();
            outboxRepository.save(outbox);
            log.info("Outbox {}: evento {} publicado para transferencia {} ({})",
                outbox.getId(), evento.tipo(), evento.transferenciaId(), evento.eventoId());
            return true;
        });

        return Boolean.TRUE.equals(procesado);
    }
}
