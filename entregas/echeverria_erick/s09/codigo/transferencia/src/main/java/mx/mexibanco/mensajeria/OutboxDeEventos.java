package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.transferencia.Transferencia;
import mx.mexibanco.transferencia.TransferenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La mitad "escribir" del Transactional Outbox. Guarda el estado final de la transferencia y el
 * evento en UNA sola transaccion local: o quedan las dos filas o ninguna. La saga no llama a
 * RabbitMQ; eso lo hace RelayDeOutbox despues.
 *
 * Se usa un TransactionTemplate y no @Transactional porque la saga llama a este metodo desde otra
 * clase pero el @Transactional de un metodo llamado desde la misma clase no hace nada.
 */
@Component
public class OutboxDeEventos {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeEventos.class);

    private final TransferenciaRepository transferencias;
    private final EventoSalienteRepository outbox;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public OutboxDeEventos(TransferenciaRepository transferencias, EventoSalienteRepository outbox,
                           ObjectMapper json, PlatformTransactionManager transacciones) {
        this.transferencias = transferencias;
        this.outbox = outbox;
        this.json = json;
        this.tx = new TransactionTemplate(transacciones);
    }

    /** Guarda la transferencia (ya en su estado final) y deja el evento en el outbox, todo junto. */
    public Transferencia guardarYEncolar(Transferencia t, EventoTransferencia evento) {
        String payload;
        try {
            payload = json.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.eventoId(), e);
        }
        Transferencia guardada = tx.execute(status -> {
            Transferencia fila = transferencias.save(t);
            outbox.save(new EventoSaliente(evento.eventoId(), evento.routingKey(), payload));
            return fila;
        });
        log.info("Transferencia {}: {} y su evento {} ({}) quedaron en el outbox",
            guardada.getId(), guardada.getEstado(), evento.tipo(), evento.eventoId());
        return guardada;
    }
}