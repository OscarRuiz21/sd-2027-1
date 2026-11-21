package mx.mexibanco.transferencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.transferencia.mensajeria.TransferenciaEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final String EXCHANGE = "transferencias";

    private final TransferenciaRepository transferencias;
    private final OutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper;

    public OutboxService(TransferenciaRepository transferencias, OutboxRepository outbox,
                         RabbitTemplate rabbitTemplate, ObjectMapper mapper) {
        this.transferencias = transferencias;
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
    }

    /** Guarda el estado de la transferencia y su evento en UNA transaccion: o quedan los dos, o ninguno. */
    @Transactional
    public Transferencia guardarConEvento(Transferencia t, String tipo, String routingKey,
                                          BigDecimal saldoOrigen, BigDecimal saldoDestino) {
        Transferencia guardada = transferencias.save(t);
        log.info("Transferencia {}: {}", guardada.getId(), guardada.getEstado());

        TransferenciaEvento evento = new TransferenciaEvento(
                "transferencia-" + guardada.getId() + "-" + tipo,
                tipo,
                guardada.getId(),
                guardada.getClaveOrigen(),
                guardada.getClaveDestino(),
                guardada.getMonto(),
                saldoOrigen,
                saldoDestino,
                guardada.getFecha());
        try {
            outbox.save(new Outbox(evento.eventoId(), routingKey, mapper.writeValueAsString(evento)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.eventoId(), e);
        }
        log.info("Transferencia {}: evento {} guardado en el outbox", guardada.getId(), tipo);
        return guardada;
    }

    /** Lo llama el relay. Publica las filas pendientes y las marca. Regresa cuantas publico. */
    @Transactional
    public int publicarPendientes() {
        List<Outbox> filas = outbox.pendientes(20);
        int publicadas = 0;
        for (Outbox fila : filas) {
            try {
                Message mensaje = MessageBuilder
                        .withBody(fila.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build();
                rabbitTemplate.invoke(ops -> {
                    ops.send(EXCHANGE, fila.getRoutingKey(), mensaje);
                    ops.waitForConfirmsOrDie(5000);   // el broker confirmo que lo recibio
                    return null;
                });
                fila.marcarPublicada();
                publicadas++;
                log.info("Outbox {}: evento {} publicado con clave {}", fila.getId(), fila.getEventoId(), fila.getRoutingKey());
            } catch (Exception e) {
                log.warn("Outbox {}: no se pudo publicar ({}); queda pendiente y se reintenta", fila.getId(), e.getMessage());
                break;   // si RabbitMQ esta caido, no insistas con las demas filas en esta vuelta
            }
        }
        return publicadas;
    }
}