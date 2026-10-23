package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * La mitad "publicar" del Transactional Outbox. Cada segundo toma las filas pendientes, las publica
 * con PublicadorDeEventos (que espera la confirmacion del broker) y las marca como publicadas.
 *
 * - Si el broker esta caido o no confirma, la fila se queda pendiente y se vuelve a intentar en la
 *   siguiente vuelta. Se corta el lote en la primera falla para no esperar el timeout por cada fila.
 * - Si el relay publica y muere antes del commit, la fila sigue pendiente y el evento sale OTRA vez
 *   con el mismo eventoId. Ese duplicado lo absorbe la idempotencia del consumidor (tarea 5).
 */
@Component
public class RelayDeOutbox {

    private static final Logger log = LoggerFactory.getLogger(RelayDeOutbox.class);
    private static final int LOTE = 20;

    private final EventoSalienteRepository outbox;
    private final PublicadorDeEventos publicador;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public RelayDeOutbox(EventoSalienteRepository outbox, PublicadorDeEventos publicador,
                         ObjectMapper json, PlatformTransactionManager transacciones) {
        this.outbox = outbox;
        this.publicador = publicador;
        this.json = json;
        this.tx = new TransactionTemplate(transacciones);
    }

    @Scheduled(fixedDelayString = "${mexibanco.mensajeria.relay-ms:1000}",
        initialDelayString = "${mexibanco.mensajeria.relay-ms:1000}")
    void publicarPendientes() {
        Integer publicados = tx.execute(status -> {
            List<EventoSaliente> pendientes = outbox.pendientesParaPublicar(LOTE);
            int salieron = 0;
            for (EventoSaliente fila : pendientes) {
                try {
                    EventoTransferencia evento = json.readValue(fila.getPayload(), EventoTransferencia.class);
                    publicador.publicar(evento);               // espera el ack del broker
                } catch (EventoNoPublicadoException sinBroker) {
                    log.warn("Outbox: el evento {} sigue pendiente: {}", fila.getEventoId(), sinBroker.getMessage());
                    break;                                     // sin broker no tiene caso seguir con las demas
                } catch (JsonProcessingException ilegible) {
                    log.error("Outbox: la fila {} no se puede leer, se salta: {}", fila.getId(), ilegible.getOriginalMessage());
                    continue;
                }
                fila.marcarPublicado();                        // se guarda al hacer commit
                salieron++;
            }
            return salieron;
        });
        if (publicados != null && publicados > 0) {
            log.info("Outbox: {} evento(s) publicado(s) y confirmado(s) por el broker", publicados);
        }
    }
}